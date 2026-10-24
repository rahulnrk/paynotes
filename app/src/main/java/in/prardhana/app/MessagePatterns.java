package in.prardhana.app;

import java.util.*;
import java.util.regex.*;

/** A deliberately lossy grammar: no arbitrary words, IDs, senders, or amounts survive. */
public final class MessagePatterns {
    private static final Set<String> VOCAB=Set.of("inr","rs","₹","debited","credited","paid","received","sent","transferred","transfer","to","from","at","on","by","for","via","upi","upiref","ref","refno","reference","no","txn","transactionid","id","imps","neft","rtgs","account","ac","bal","balance","available","avl","otp","failed","declined","due","will","be","your","has","been","of","is","with","amount","transaction","payment","purchase","using","ending","bank","a","c","card","credit","debit","atm","cash","withdrawn","withdrawal","refund","refunded","reversed","reversal","salary","interest","dividend","emi","loan","mandate","autopay","subscription","bill","minimum","total","recharge","successful","successfully","success","pending","request","requested","scheduled","limit","outstanding","if","already","ignore","charges","fee","tax","cheque","chq","returned","return","rrn","creditedto","towards","deposited","deposit","reward","cashback","creditedwith","cancelled","canceled","rejected","insufficient","funds","acct","not","processed","presented","maximum","ceiling");
    public static String shape(String text){
        Matcher tokens=Pattern.compile("[\\p{L}]+|[0-9]+|₹").matcher(text.toLowerCase(Locale.ROOT));StringBuilder out=new StringBuilder();String previous="";int count=0;
        while(tokens.find()&&count++<120){String token=tokens.group();String value=VOCAB.contains(token)?token:Character.isDigit(token.charAt(0))?"<number>":"<word>";if(value.equals(previous)&&(value.equals("<word>")||value.equals("<number>")))continue;out.append(value).append(' ');previous=value;}
        return out.toString().trim();
    }
    public static String family(String text){return family(text,null);}
    public static String family(String text,String sender){
        String s=text.toLowerCase(Locale.ROOT);
        if(s.matches("(?s).*\\b(?:otp|verification code|one.time password)\\b.*"))return "OTP / verification";
        if(s.matches("(?s).*\\b(?:failed|declined|unsuccessful|rejected)\\b.*"))return "Failed payment";
        TransactionParser.Parsed p=TransactionParser.parse(text,sender);
        if(p!=null)return p.category().equals("Payment receipt")?"Payment acknowledgement":"Completed "+p.direction();
        if(s.matches("(?s).*\\b(?:reversed|reversal)\\b.*"))return "Reversal · review";
        if(s.matches("(?s).*(?:will be|to be|payment request|collect request|scheduled|pending).*"))return "Pending / future / request";
        if(s.matches("(?s).*\\b(?:(?:is|amount|minimum|total|bill)\\s+due|due on|payment due|already paid|outstanding)\\b.*"))return "Bill reminder / statement";
        if(s.matches("(?s).*\\b(?:recharge|talktime)\\b.*")&&!s.matches("(?s).*\\b(?:debited|paid|spent)\\b.*"))return "Recharge / telecom information";
        if(s.matches("(?s).*(?:₹|rs\\.?|inr).*\\b(?:debited|credited|paid|received|transferred|spent|withdrawn|refunded)\\b.*")||s.matches("(?s).*\\b(?:debited|credited|paid|received|transferred|spent|withdrawn|refunded)\\b.*(?:₹|rs\\.?|inr).*"))return "Financial format · unmatched";
        if(s.matches("(?s).*\\b(?:balance|statement|limit)\\b.*"))return "Balance / account information";
        if(s.matches("(?s).*\\b(?:offer|discount|cashback|reward|sale)\\b.*"))return "Promotion / reward";
        return "Other";
    }
}
