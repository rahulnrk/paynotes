package in.prardhana.app;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Explicit opt-in, finite-vocabulary diagnostic counts. No arbitrary text leaves the phone. */
final class Learning {
    static synchronized void observe(Context c,String text,String sender,TransactionParser.Parsed parsed,String observation){
        if(!Store.prefs(c).getBoolean("pilot_consent",false)||text.isBlank()||MessagePatterns.family(text).equals("OTP / verification"))return;
        SQLiteDatabase seen=LedgerDb.get(c).getWritableDatabase();try(Cursor version=seen.rawQuery("SELECT value FROM meta WHERE key='diagnostic_version'",null)){if(!version.moveToFirst()||!version.getString(0).equals(String.valueOf(TransactionParser.VERSION))){seen.delete("diagnostics",null,null);seen.delete("diagnostic_seen",null,null);seen.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES('diagnostic_version',?)",new Object[]{String.valueOf(TransactionParser.VERSION)});}}ContentValues mark=new ContentValues();mark.put("id",TransactionParser.VERSION+"|"+observation);mark.put("time",System.currentTimeMillis());if(seen.insertWithOnConflict("diagnostic_seen",null,mark,SQLiteDatabase.CONFLICT_IGNORE)==-1)return;
        String shape=MessagePatterns.shape(text);if(shape.isEmpty())return;String header=Issuer.header(sender);String issuer=Issuer.fromSender(sender);if(issuer.isEmpty())issuer="Other";
        String outcome=parsed==null?(FinancialFacts.read(text).event().isEmpty()?"unmatched":"event"):parsed.category().equals("Payment receipt")?"receipt":"movement";
        SQLiteDatabase db=LedgerDb.get(c).getWritableDatabase();ContentValues counter=new ContentValues();counter.put("shape",shape);counter.put("issuer",issuer);counter.put("header",header);counter.put("outcome",outcome);counter.put("count",0);long inserted=db.insertWithOnConflict("diagnostics",null,counter,SQLiteDatabase.CONFLICT_IGNORE);db.execSQL("UPDATE diagnostics SET count=MIN(count+1,1000000000) WHERE shape=? AND issuer=? AND header=? AND outcome=?",new Object[]{shape,issuer,header,outcome});
        if(inserted!=-1)db.execSQL("DELETE FROM diagnostics WHERE rowid IN (SELECT rowid FROM diagnostics ORDER BY count ASC LIMIT MAX(0,(SELECT COUNT(*) FROM diagnostics)-1000))");
    }
    static JSONObject snapshot(Context c)throws JSONException{
        JSONObject counts=new JSONObject();SQLiteDatabase db=LedgerDb.get(c).getReadableDatabase();
        for(String k:new String[]{"transactions","reviewed","unknown","excluded","receipts"}){String where=switch(k){case "reviewed"->" WHERE reviewed=1";case "unknown"->" WHERE merchant='Unknown merchant'";case "excluded"->" WHERE category='Not a transaction'";case "receipts"->" WHERE category='Payment receipt'";default->"";};try(Cursor cur=db.rawQuery("SELECT COUNT(*) FROM movements"+where,null)){cur.moveToFirst();counts.put(k,cur.getLong(0));}}
        try(Cursor cur=db.rawQuery("SELECT COUNT(*) FROM events",null)){cur.moveToFirst();counts.put("events",cur.getLong(0));}
        for(String k:new String[]{"duplicates","corrections","opens","helpful","unhelpful"})counts.put(k,Math.max(0,Store.prefs(c).getInt(k,0)));
        JSONArray patterns=new JSONArray();try(Cursor cur=db.rawQuery("SELECT shape,issuer,outcome,count,header FROM diagnostics ORDER BY (outcome='unmatched') DESC,count DESC LIMIT 100",null)){while(cur.moveToNext())patterns.put(new JSONObject().put("shape",cur.getString(0)).put("issuer",cur.getString(1)).put("outcome",cur.getString(2)).put("count",cur.getLong(3)).put("header",cur.getString(4)));}
        return new JSONObject().put("schema",2).put("parserVersion",TransactionParser.VERSION).put("appVersion",BuildConfig.VERSION_NAME).put("exportedAt",System.currentTimeMillis()).put("budgetEnabled",Store.prefs(c).getLong("budget",0)>0).put("captureEnabled",Store.prefs(c).getBoolean("capture",false)).put("smsEnabled",Store.prefs(c).getBoolean("sms_capture",false)).put("notificationConnected",CaptureService.isConnected()).put("counters",counts).put("patterns",patterns);
    }
    static synchronized void report(Context c,String shape,String issuer,String issue,String expected){
        if(!Store.prefs(c).getBoolean("pilot_consent",false))throw new IllegalStateException("Enable pilot sharing first");
        try{String salt=Store.prefs(c).getString("pilot_case_salt","");if(salt.isEmpty()){salt=UUID.randomUUID().toString();Store.prefs(c).edit().putString("pilot_case_salt",salt).apply();}String identity=salt+"|"+TransactionParser.VERSION+"|"+shape+"|"+issuer+"|"+issue+"|"+expected;String id=UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();JSONObject r=new JSONObject().put("id",id).put("parserVersion",TransactionParser.VERSION).put("issue",issue).put("expected",expected).put("shape",shape).put("issuer",issuer.isEmpty()?"Other":issuer);
            ContentValues v=new ContentValues();v.put("id",id);v.put("data",r.toString());LedgerDb.get(c).getWritableDatabase().insertWithOnConflict("reports",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static void correction(Context c,JSONObject before,JSONObject after){
        if(!Store.prefs(c).getBoolean("pilot_consent",false)||before.optString("shape").isEmpty())return;
        if(before.optString("merchant").equals(after.optString("merchant"))&&before.optString("category").equals(after.optString("category"))&&before.optString("direction").equals(after.optString("direction")))return;
        String expected=after.optString("category").equals("Not a transaction")?"ignore":after.optString("category").equals("Payment receipt")?"receipt":after.optString("direction");
        report(c,before.optString("shape"),before.optString("issuer"),"user_correction",expected);
    }
}
