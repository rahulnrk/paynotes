package in.prardhana.app;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Optional facts, independently labelled; no full message or sender is retained. */
public final class FinancialFacts {
    public record Facts(String date,String clock,String account,String issuer,Long balance,String event,String dueDate,long amount) {}
    static String date(String text){
        Matcher m=Pattern.compile("(?i)\\b(\\d{1,2})[- /]?(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- /]?(\\d{4}|\\d{2})\\b").matcher(text);
        try{if(m.find()){int year=Integer.parseInt(m.group(3));if(year<100)year+=2000;int month=List.of("jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec").indexOf(m.group(2).toLowerCase(Locale.ROOT))+1;return LocalDate.of(year,month,Integer.parseInt(m.group(1))).toString();}
            m=Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").matcher(text);if(m.find())return LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))).toString();
            m=Pattern.compile("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})\\b").matcher(text);if(m.find()){int year=Integer.parseInt(m.group(3));if(year<100)year+=2000;return LocalDate.of(year,Integer.parseInt(m.group(2)),Integer.parseInt(m.group(1))).toString();}
        }catch(DateTimeException|NumberFormatException ignored){}return "";
    }
    static Long amount(String value){try{return new java.math.BigDecimal(value.replace(",","").replace(" ","")).movePointRight(2).longValueExact();}catch(RuntimeException e){return null;}}
    public static Facts read(String text){return read(text,System.currentTimeMillis());}
    public static Facts read(String text,long arrivedAt){return read(text,arrivedAt,"");}
    public static Facts read(String text,long arrivedAt,String issuerContext){
        String s=text.toLowerCase(Locale.ROOT),issuer=issuerContext.isEmpty()?Issuer.inText(text):issuerContext;
        Matcher a=Pattern.compile("(?i)\\b(?:a\\s*/\\s*c|acct|account|acc|ac|card)\\s*(?:no\\.?|number|ending)?\\s*[:.-]?\\s*([xX*]+[0-9]{2,6})\\b").matcher(text);
        String account=a.find()?(a.group().toLowerCase(Locale.ROOT).startsWith("card")?"Card ":"Account ")+"••"+a.group(1).replaceAll("[^0-9]",""):"";
        // Explicit available/new bank balance only. Never derive a balance by summing the ledger.
        Matcher b=Pattern.compile("(?i)\\b(?:avl\\.?\\s*bal(?:ance)?|available\\s+balance|new\\s+bal(?:ance)?|bal(?:ance)?)\\s*(?:in your a/c\\s*)?(?:is\\s*)?[:.]?\\s*(?:₹|rs\\.?|inr\\.?)\\s*(-?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?)").matcher(text);
        Long balance=b.find()?amount(b.group(1)):null;
        if(issuer.isEmpty()||account.isEmpty())balance=null;
        String event="";
        boolean payment=s.matches("(?s).*\\b(?:payment|debit|debited|transaction|mandate|autopay|emi|nach|bank|refund)\\b.*");
        if(payment&&s.matches("(?s).*\\b(?:not debited|failed|declined|could not be processed|cbs rejection|bounced|returned with the reason)\\b.*"))event="Failed payment";
        else if(s.matches("(?s).*\\b(?:will be refunded|refund.{0,80}initiated)\\b.*"))event="Pending refund";
        else if(s.matches("(?s).*\\brefund\\b.*\\b(?:processed|received|completed)\\b.*"))event="Provider refund notice";
        else if(s.matches("(?s).*\\b(?:received as repayment|credited to your vi number)\\b.*"))event="Provider receipt";
        else if(s.matches("(?s).*\\b(?:mandate|autopay)\\b.*\\b(?:cancelled|revoked|paused)\\b.*"))event="Mandate changed";
        else if(s.matches("(?s).*\\b(?:requested money|request money|collect request|on approving|on approval)\\b.*"))event="Payment request";
        else if(s.matches("(?s).*\\b(?:scheduled|will be debited|to be debited|will be presented)\\b.*"))event="Scheduled payment";
        else if(s.matches("(?s).*\\b(?:overdue|amount due|payment due|due on|emi.{0,40}due)\\b.*"))event="Bill or EMI due";
        else if(s.matches("(?s).*\\byour beneficiary\\b.*\\breceived\\b.*"))event="Outgoing confirmation";
        if(s.matches("(?s).*\\b(?:get up to|get upto|get instant|can be credited|welcome bonus|free cash|apply now|approval up to|zero documentation|coupon|voucher)\\b.*"))event="";
        Matcher amt=Pattern.compile("(?i)(?:₹|rs\\.?|inr\\.?)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)").matcher(text);Long paise=amt.find()?amount(amt.group(1)):null;
        Matcher time=Pattern.compile("(?<![\\d.])(?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?!\\d)").matcher(text);
        String day=date(text),clock=time.find()?time.group():"";
        // Only a date immediately following an execution/due label is an actionable due date.
        Matcher due=Pattern.compile("(?i)\\b(?:scheduled on|due on|debited on|presented.{0,15}on|credited by)\\s*[.:]?\\s*(.{1,22})").matcher(text);
        String dueDay=due.find()?date(due.group(1)):"";
        if(dueDay.isEmpty()&&List.of("Scheduled payment","Bill or EMI due","Pending refund").contains(event))dueDay=executionDate(text,arrivedAt,event);
        if(event.equals("Payment request"))dueDay="";
        return new Facts(day,clock,account,issuer,balance,event,dueDay,paise==null?0:paise);
    }
    static String executionDate(String text,long arrivedAt,String event){
        String cleaned=text.replaceAll("(?i)(\\d)(?:st|nd|rd|th)\\b","$1").replaceAll("(?<=\\d)'(?=\\d{2}\\b)","-").replaceAll("(?i)([a-z]{3})'(?=\\d{2}\\b)","$1-");
        Matcher m=Pattern.compile("(?is)\\b(?:due(?:\\s+on)?|scheduled(?:\\s+on)?|presented|debited|credited by|execution date|date:)\\b(.{0,200})").matcher(cleaned);
        if(!m.find())return "";String tail=m.group(1);
        // Prefer an explicitly labelled execution date over dates in unrelated text.
        Matcher on=Pattern.compile("(?i)\\b(?:on|by|date:)\\s*[.:]?\\s*(.{1,25})").matcher(tail);String value=on.find()?on.group(1):tail.trim();
        String full=date(value);if(!full.isEmpty())return full;
        Matcher shortDate=Pattern.compile("(?i)^\\s*[.:]?\\s*(\\d{1,2})[- /](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b").matcher(value);
        if(!shortDate.find())return "";
        LocalDate anchor=Instant.ofEpochMilli(arrivedAt).atZone(ZoneId.systemDefault()).toLocalDate();int month=List.of("jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec").indexOf(shortDate.group(2).toLowerCase(Locale.ROOT))+1;
        try{LocalDate day=LocalDate.of(anchor.getYear(),month,Integer.parseInt(shortDate.group(1)));if(text.toLowerCase(Locale.ROOT).contains("overdue")){if(day.isAfter(anchor))day=day.minusYears(1);}else{if(day.isBefore(anchor.minusMonths(6)))day=day.plusYears(1);else if(day.isAfter(anchor.plusMonths(6)))day=day.minusYears(1);}return day.toString();}catch(DateTimeException e){return "";}
    }

}
