package in.prardhana.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import org.json.*;
import java.util.*;

final class PatternLab {
    static JSONArray history(Context c){try{return new JSONArray(Store.prefs(c).getString("pattern_studies","[]"));}catch(JSONException e){return new JSONArray();}}
    static JSONObject scan(Context c,long generation,java.util.function.IntConsumer progress) throws JSONException {
        if(c.checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException();
        Map<String,Integer> families=new TreeMap<>(),shapes=new HashMap<>(),types=new TreeMap<>();int total=0,received=0,sent=0,candidates=0,unknown=0,missing=0,vpas=0;
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"body","type","address"},null,null,"date DESC")){
            if(cursor==null)throw new IllegalStateException("Inbox unavailable");
            while(cursor.moveToNext()){
                if(c.checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED||Store.prefs(c).getLong("generation",0)!=generation)throw new SecurityException();
                String text=cursor.getString(0);if(text==null)text="";total++;int folder=cursor.getInt(1);if(folder==1)received++;else if(folder==2)sent++;
                String family=MessagePatterns.family(text,cursor.getString(2));families.merge(family,1,Integer::sum);
                if(!family.equals("Other")&&!family.equals("OTP / verification")&&!family.equals("Promotion / reward"))shapes.merge(family+" | "+MessagePatterns.shape(text),1,Integer::sum);
                TransactionParser.Parsed p=TransactionParser.parse(text,cursor.getString(2));if(folder==1&&p!=null){candidates++;if(p.merchant().equals("Unknown merchant"))unknown++;if(p.reference().isEmpty())missing++;if(!p.vpa().isEmpty())vpas++;types.merge(p.type(),1,Integer::sum);}
                if(total%1000==0)progress.accept(total);
            }
        }
        JSONArray patterns=new JSONArray();for(var e:shapes.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(100).toList())patterns.put(new JSONObject().put("family",e.getKey().split(" \\| ",2)[0]).put("shape",e.getKey().split(" \\| ",2)[1]).put("count",e.getValue()));
        JSONObject report=new JSONObject().put("parserVersion",TransactionParser.VERSION).put("at",System.currentTimeMillis()).put("total",total).put("received",received).put("sent",sent).put("candidates",candidates).put("unknown",unknown).put("missingReference",missing).put("vpaPresent",vpas).put("families",new JSONObject(families)).put("types",new JSONObject(types)).put("patterns",patterns);
        synchronized(Store.class){if(Store.prefs(c).getLong("generation",0)!=generation)throw new IllegalStateException("Data was reset");JSONArray old=history(c),next=new JSONArray();for(int i=Math.max(0,old.length()-4);i<old.length();i++)next.put(old.get(i));next.put(report);Store.prefs(c).edit().putString("pattern_studies",next.toString()).apply();}
        return report;
    }
    static synchronized void observeNotification(Context c,String text){
        if(!Store.prefs(c).getBoolean("learn_patterns",false))return;
        try{JSONObject patterns=new JSONObject(Store.prefs(c).getString("notification_patterns","{}"));String shape=MessagePatterns.family(text)+" | "+MessagePatterns.shape(text);if(patterns.has(shape)||patterns.length()<100){patterns.put(shape,patterns.optLong(shape)+1);Store.prefs(c).edit().putString("notification_patterns",patterns.toString()).apply();}}catch(JSONException ignored){}
    }
    static int notificationShapes(Context c){try{return new JSONObject(Store.prefs(c).getString("notification_patterns","{}")).length();}catch(JSONException e){return 0;}}
}
