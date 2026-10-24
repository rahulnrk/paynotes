package in.prardhana.app;
import android.content.Context;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Obligation evidence is separate from executed money; matching requires a shared reference. */
final class FinancialEvents {
    static JSONArray rows(Context c){return LedgerDb.get(c).events();}
    static List<JSONObject> upcoming(Context c){
        List<JSONObject> out=new ArrayList<>();JSONArray all=rows(c);java.time.LocalDate today=java.time.LocalDate.now();
        for(int i=0;i<all.length();i++){JSONObject row=all.optJSONObject(i);if(row.optString("issuer").isEmpty()||!row.optString("source").equals("sms")||!List.of("Scheduled payment","Bill or EMI due").contains(row.optString("type"))||!row.optString("status","Open").equals("Open"))continue;
            try{java.time.LocalDate due=java.time.LocalDate.parse(row.optString("dueDate"));if(!due.isBefore(today)&&!due.isAfter(today.plusDays(30)))out.add(row);}catch(java.time.DateTimeException ignored){}}
        out.sort(Comparator.comparing(r->r.optString("dueDate")));return out;
    }
    static void observe(Context c,String text,String sender,String source,long time){observe(c,text,sender,source,time,0);}
    static void observe(Context c,String text,String sender,String source,long time,long sentAt){
        if(text.isEmpty()||text.toLowerCase(Locale.ROOT).matches("(?s).*\\b(?:otp|pass code|verification code|one.time password)\\b.*"))return;
        if(sender!=null&&(sender.toUpperCase(Locale.ROOT).matches("[A-Z]{2}-[A-Z0-9]{6}-P")||sender.replaceAll("[+\\s()-]","").matches("[0-9]{8,15}")))return;
        FinancialFacts.Facts f=FinancialFacts.read(text,time,Issuer.fromSender(sender));if(f.event().isEmpty())return;if(f.event().equals("Provider refund notice")&&Issuer.trusted(sender)&&TransactionParser.parse(text,sender)!=null)return;
        try{
            String canonical=Store.canonical(text,source,sender,sentAt),hash=canonical.isEmpty()?Store.fingerprint(text,time):canonical;
            String issuer=Issuer.fromSender(sender);if(issuer.isEmpty())issuer=f.issuer();
            JSONObject e=new JSONObject().put("id",hash).put("fingerprint",Store.fingerprint(text,time)).put("type",f.event()).put("source",source).put("time",time).put("amount",f.amount()).put("dueDate",f.dueDate()).put("dateEvidence",f.dueDate().isEmpty()?"Unknown":"Message date; year may be inferred from arrival").put("issuer",issuer).put("account",f.account()).put("status","Open").put("kind",kind(text)).put("amountMeaning",text.toLowerCase(Locale.ROOT).matches("(?s).*\\b(?:upto|up to|maximum|ceiling)\\b.*")?"Maximum authorised":"Stated amount").put("reference",reference(text));
            Matcher provider=Pattern.compile("(?i)\\b(?:towards|for|at)\\s+([a-z][a-z &.]{1,45}?)(?=\\s+(?:debit|for|on|of|using|amount)|[.;\\n]|$)").matcher(text);e.put("provider",provider.find()?provider.group(1).trim():"");
            LedgerDb.get(c).event(e);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static String kind(String text){String s=text.toLowerCase(Locale.ROOT);return s.matches("(?s).*\\b(?:emi|loan)\\b.*")?"EMI / loan":s.contains("electric")||s.contains("water bill")?"Utility":s.matches("(?s).*\\b(?:rent|society)\\b.*")?"Housing":s.contains("recharge")?"Recharge":s.contains("insurance")||s.contains("premium")?"Insurance":s.contains("subscription")||s.contains("autopay")||s.contains("mandate")?"Recurring service":"Payment";}
    static String reference(String text){Matcher m=Pattern.compile("(?i)\\b(?:utr|rrn|refno|refund ref(?:\\. num\\.)?|reference|ref|txn id)\\s*[:#.-]?\\s*((?=[a-z]*[0-9])[a-z0-9]{6,})").matcher(text);return m.find()?m.group(1).toUpperCase(Locale.ROOT):"";}
    static void resolve(Context c,String id,String status){if(!List.of("Open","Paid / received","Cancelled","Dismissed").contains(status))throw new IllegalArgumentException("Unknown status");JSONArray a=rows(c);for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e.optString("id",e.optString("fingerprint")).equals(id))try{e.put("status",status).put("statusSource","You confirmed");LedgerDb.get(c).updateEvent(e);}catch(JSONException x){throw new IllegalStateException(x);}}}
    static void reconcile(Context c,JSONObject movement){
        String ref=movement.optString("reference");if(ref.isEmpty()||List.of("Payment receipt","Not a transaction","Transfer").contains(movement.optString("category")))return;
        JSONArray events=rows(c);for(int i=0;i<events.length();i++){JSONObject e=events.optJSONObject(i);
            boolean refund=List.of("Pending refund","Provider refund notice").contains(e.optString("type"));
            if(!ref.equalsIgnoreCase(e.optString("reference"))||!e.optString("status","Open").equals("Open")||e.optLong("amount")!=movement.optLong("paise")||!Store.compatible(e.optString("issuer"),movement.optString("issuer"))||!Store.accountCompatible(e.optString("account"),movement.optString("account")))continue;
            if(refund?!movement.optString("type").equals("Refund"):!List.of("Scheduled payment","Bill or EMI due").contains(e.optString("type"))||!movement.optString("direction").equals("debit"))continue;
            try{e.put("status","Paid / received").put("statusSource","Matching reference, amount and account evidence").put("movementId",movement.optString("id"));LedgerDb.get(c).updateEvent(e);}catch(JSONException x){throw new IllegalStateException(x);}
        }
    }
    static void reconcileAll(Context c){JSONArray a=rows(c);for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e.optString("reference").isEmpty()||!e.optString("status","Open").equals("Open"))continue;JSONArray m=LedgerDb.get(c).byReference(e.optString("reference"),e.optLong("amount"),List.of("Pending refund","Provider refund notice").contains(e.optString("type"))?"credit":"debit");if(m.length()==1)reconcile(c,m.optJSONObject(0));}}
    static void facts(JSONObject row,String text,long sentAt)throws JSONException{facts(row,text,sentAt,null);}
    static void facts(JSONObject row,String text,long sentAt,String sender)throws JSONException{
        FinancialFacts.Facts f=FinancialFacts.read(text,row.optLong("time"),Issuer.fromSender(sender));String source=row.optString("source");row.put("smsSentAt",sentAt).put("messageDate",f.date()).put("messageClock",f.clock()).put("timeSource",source.equals("sms")?"SMS arrival":source.equals("manual")||source.equals("sample")?"Added manually":"Notification posted").put("account",f.account()).put("issuer",f.issuer());
        if(f.balance()!=null)row.put("reportedBalance",f.balance());else row.remove("reportedBalance");
    }
}
