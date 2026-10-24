package in.prardhana.app;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;
import org.json.*;
import java.util.*;

/** Full SMS corpus audit. Raw text is read only in phone memory and never returned. */
public class PatternStudy extends Instrumentation {
    private boolean authorized;
    @Override public void onCreate(Bundle args){super.onCreate(args);authorized="full-local-study".equals(args.getString("authorization"));start();}
    @Override public void onStart(){Bundle result=new Bundle();if(!authorized){result.putString("stream","REFUSED: explicit full-local-study authorization required");finish(0,result);return;}
        try{Context c=getTargetContext();Map<String,Integer> families=new TreeMap<>(),shapes=new HashMap<>(),handles=new TreeMap<>(),folders=new TreeMap<>(),channels=new TreeMap<>(),types=new TreeMap<>(),signals=new TreeMap<>(),specialShapes=new HashMap<>(),risks=new TreeMap<>();int total=0,unknown=0,parsed=0,missingRef=0;
            Set<String> known=Set.of("okaxis","okhdfcbank","oksbi","okicici","ybl","ibl","axl","paytm","upi","apl","axisbank","icici","hdfcbank","sbi","yesbank","ptyes","ptaxis","pthdfc","ptys","superyes","slice","amazonpay");
            try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"body","type"},null,null,"date DESC")){
                if(cursor==null)throw new IllegalStateException();
                while(cursor.moveToNext()){String text=cursor.getString(0);if(text==null)text="";int folder=cursor.getInt(1);String folderName=folder==1?"Received":folder==2?"Sent":folder==3?"Draft":"Other folder";folders.merge(folderName,1,Integer::sum);total++;
                    String family=MessagePatterns.family(text);families.merge(family,1,Integer::sum);
                    if(!family.equals("Other")&&!family.equals("OTP / verification")&&!family.equals("Promotion / reward"))shapes.merge(family+" | "+MessagePatterns.shape(text),1,Integer::sum);
                    TransactionParser.Parsed p=TransactionParser.parse(text);if(p!=null){parsed++;if(p.merchant().equals("Unknown merchant"))unknown++;if(p.reference().isEmpty())missingRef++;channels.merge(p.channel(),1,Integer::sum);types.merge(p.type(),1,Integer::sum);}
                    String lower=text.toLowerCase(Locale.ROOT);String signal=lower.matches("(?s).*\\b(?:card|creditcard)\\b.*")?"Card-related":lower.contains("upi")?"UPI-related":lower.matches("(?s).*\\b(?:imps|neft|rtgs)\\b.*")?"Bank transfer-related":"";
                    if(!signal.isEmpty()){String disposition=p==null?"not parsed":"parsed "+p.direction()+" / "+p.type();signals.merge(signal+" | "+disposition,1,Integer::sum);specialShapes.merge(signal+" | "+disposition+" | "+MessagePatterns.shape(text),1,Integer::sum);}
                    if(lower.matches("(?s).*\\b(?:usd|eur|gbp|aed|sgd|aud|jpy)\\b.*")){risks.merge("Foreign currency mentioned",1,Integer::sum);if(p!=null)risks.merge("Parsed despite foreign currency mention · inspect",1,Integer::sum);}
                    if(p!=null&&p.direction().equals("credit")&&lower.matches("(?s).*\\b(?:card|creditcard)\\b.*")&&lower.matches("(?s).*\\b(?:payment|paid|received)\\b.*")&&!p.type().equals("Refund"))risks.merge("Possible card-repayment acknowledgement parsed as credit",1,Integer::sum);
                    if(p!=null&&p.merchant().toLowerCase(Locale.ROOT).matches(".*\\b(?:limit|available|balance|statement)\\b.*"))risks.merge("Merchant label contains account metadata · inspect",1,Integer::sum);
                    if(total%5000==0){Bundle progress=new Bundle();progress.putString("stream","AUDIT_PROGRESS "+total+" messages\n");sendStatus(1,progress);}
                    java.util.regex.Matcher vpa=java.util.regex.Pattern.compile("(?i)[a-z0-9._-]+@([a-z][a-z0-9]+)\\b").matcher(text);while(vpa.find()){String handle=vpa.group(1).toLowerCase(Locale.ROOT);handles.merge(known.contains(handle)?handle:"other / unverified",1,Integer::sum);}
                }
            }
            JSONArray patterns=new JSONArray();shapes.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(60).forEach(e->{try{patterns.put(new JSONObject().put("shape",e.getKey()).put("count",e.getValue()));}catch(JSONException ex){throw new IllegalStateException(ex);}});
            JSONArray special=new JSONArray();for(String group:List.of("Card-related","UPI-related","Bank transfer-related"))specialShapes.entrySet().stream().filter(e->e.getKey().startsWith(group+" | ")).sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(12).forEach(e->{try{special.put(new JSONObject().put("shape",e.getKey()).put("count",e.getValue()));}catch(JSONException error){throw new IllegalStateException(error);}});
            JSONObject report=new JSONObject().put("scope","All available SMS folders; read-only analysis").put("total",total).put("folders",new JSONObject(folders)).put("families",new JSONObject(families)).put("parsedCandidates",parsed).put("unknownMerchant",unknown).put("missingReference",missingRef).put("channels",new JSONObject(channels)).put("transactionTypes",new JSONObject(types)).put("signalCoverage",new JSONObject(signals)).put("riskFlagsNotGroundTruth",new JSONObject(risks)).put("publicPspHandles",new JSONObject(handles)).put("patterns",patterns).put("cardAndUpiPatterns",special);
            result.putString("stream",report.toString(2)+"\n");finish(-1,result);
        }catch(Throwable error){result.putString("stream","Study failed: "+error.getClass().getSimpleName()+"\n");finish(0,result);}
    }
}
