package in.prardhana.app;

import android.app.Instrumentation;
import android.os.Bundle;
import android.database.Cursor;
import android.provider.Telephony;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Read-only field-presence audit. Never emits field values or original text. */
public class FieldStudy extends Instrumentation {
    private boolean authorized;
    @Override public void onCreate(Bundle args){super.onCreate(args);authorized="full-local-study".equals(args.getString("authorization"));start();}
    private static boolean has(String text,String regex){return Pattern.compile(regex,Pattern.CASE_INSENSITIVE).matcher(text).find();}
    private static void count(Map<String,Integer> m,String key,boolean present){if(present)m.merge(key,1,Integer::sum);}
    @Override public void onStart(){Bundle result=new Bundle();if(!authorized){result.putString("stream","REFUSED");finish(0,result);return;}
        try{
            Map<String,Integer> fields=new TreeMap<>(),timing=new TreeMap<>(),groups=new TreeMap<>(),shapes=new TreeMap<>();
            int scanned=0,candidates=0;
            try(Cursor cur=getTargetContext().getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,new String[]{"body","date","date_sent"},null,null,"date DESC")){
                if(cur==null)throw new IllegalStateException();
                while(cur.moveToNext()){
                    scanned++;String body=cur.getString(0);if(body==null)continue;
                    TransactionParser.Parsed p=TransactionParser.parse(body);if(p==null)continue;candidates++;
                    groups.merge(p.channel()+" / "+p.type(),1,Integer::sum);
                    count(fields,"Amount",true);count(fields,"Extracted counterparty name",!p.merchant().equals("Unknown merchant"));count(fields,"Extracted reference",!p.reference().isEmpty());count(fields,"UPI ID",!p.vpa().isEmpty());
                    count(fields,"Account identifier wording",has(body,"\\b(?:a\\s*/\\s*c|account|ac)\\b"));
                    count(fields,"Card identifier wording",has(body,"\\bcard\\b.{0,25}(?:ending|xx|[*])"));
                    count(fields,"Balance wording",has(body,"\\b(?:bal|balance)\\b"));
                    count(fields,"Credit limit wording",has(body,"\\b(?:available|avl|credit)\\s+limit\\b"));
                    count(fields,"Fee or charge wording (may be footer)",has(body,"\\b(?:fee|fees|charges)\\b"));
                    count(fields,"Support or dispute instructions",has(body,"\\b(?:if not|unauthori[sz]ed|call|dispute|report|helpline)\\b"));
                    count(fields,"URL",has(body,"https?://|www\\."));
                    boolean numeric=has(body,"\\b(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})\\b");
                    boolean named=has(body,"\\b\\d{1,2}(?:[- /]?)(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*(?:[- /]?)\\d{2,4}\\b");
                    boolean clock=has(body,"(?<![\\d.])(?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?!\\d)");
                    boolean ampm=has(body,"\\b(?:0?[1-9]|1[0-2])(?::[0-5]\\d)?\\s*(?:am|pm)\\b");
                    boolean zone=has(body,"\\b(?:IST|UTC|GMT)\\b|[+]05:30");
                    count(timing,"SMS arrival timestamp available",cur.getLong(1)>0);
                    count(timing,"SMS sender timestamp available",cur.getLong(2)>0);
                    count(timing,"Numeric date in body",numeric);count(timing,"Month-name date in body",named);
                    count(timing,"Clock time in body",clock||ampm);count(timing,"Explicit timezone in body",zone);
                    count(timing,"Body date and clock",(numeric||named)&&(clock||ampm));
                    count(timing,"Body date without clock",(numeric||named)&&!clock&&!ampm);
                    count(timing,"Neither body date nor clock",!numeric&&!named&&!clock&&!ampm);
                    if(cur.getLong(1)>0&&cur.getLong(2)>0){long delta=cur.getLong(1)-cur.getLong(2);count(timing,"Sender/arrival delta negative",delta<0);count(timing,"Sender/arrival delta 0–60 seconds",delta>=0&&delta<=60000);count(timing,"Sender/arrival delta over 60 seconds",delta>60000);}
                    String kind=(numeric?"numeric-date ":named?"month-name-date ":"no-date ")+(clock||ampm?"clock":"no-clock");
                    Matcher afterOn=Pattern.compile("(?i)\\bon\\s+(.{1,55}?)(?=\\s+to\\s|\\s+towards\\s|$)").matcher(body);
                    if(afterOn.find()){
                        String structure=afterOn.group(1).replaceAll("[\\p{L}]","a").replaceAll("[\\p{N}]","0");
                        shapes.merge("After-on character structure (all letters/digits masked) | "+structure,1,Integer::sum);
                    }
                    shapes.merge(p.channel()+" | "+kind+" | "+MessagePatterns.shape(body),1,Integer::sum);
                }
            }
            JSONArray patterns=new JSONArray();shapes.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(16).forEach(e->{try{patterns.put(new JSONObject().put("maskedShape",e.getKey()).put("count",e.getValue()));}catch(JSONException ex){throw new IllegalStateException(ex);}});
            result.putString("stream",new JSONObject().put("receivedScanned",scanned).put("parsedCandidates",candidates).put("fieldsPresenceNotSemanticValidation",new JSONObject(fields)).put("timingPresenceNotEventTimeValidation",new JSONObject(timing)).put("groups",new JSONObject(groups)).put("patterns",patterns).toString(2));finish(-1,result);
        }catch(Throwable e){result.putString("stream","Field study failed: "+e.getClass().getSimpleName());finish(0,result);}
    }
}
