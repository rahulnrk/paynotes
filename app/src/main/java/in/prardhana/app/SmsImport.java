package in.prardhana.app;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import java.util.*;

final class SmsImport {
    record Result(int scanned, int added, int duplicate, int refreshed, int excluded, boolean truncated) {}
    static Result recent(Context c, int days, long generation) {
        if(c.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("SMS reading permission is not granted.");
        long cutoff = System.currentTimeMillis() - days * 86400000L;
        int scanned=0, added=0, duplicates=0,refreshed=0,excluded=0; boolean truncated=false;
        List<Store.Candidate> batch=new ArrayList<>();
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{Telephony.Sms.BODY, Telephony.Sms.DATE,Telephony.Sms.ADDRESS,Telephony.Sms.DATE_SENT}, days>0?"date >= ?":null, days>0?new String[]{Long.toString(cutoff)}:null, "date DESC")) {
            if(cursor==null) throw new IllegalStateException("SMS inbox is unavailable on this device.");
            while(cursor.moveToNext()) {
                if(days>0&&scanned>=5000) {truncated=true;break;}
                synchronized(Store.class) {
                    if(c.checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("SMS access was revoked.");
                    if(!Store.prefs(c).getBoolean("sms_capture",false) || Store.prefs(c).getLong("generation",0)!=generation)
                        throw new IllegalStateException("Import cancelled because capture was disabled or data was deleted.");
                    String body=cursor.getString(0);if(body==null)body="";long time=cursor.getLong(1);
                    String sender=cursor.getString(2);
                    batch.add(new Store.Candidate(TransactionParser.parse(body,sender),Store.fingerprint(body,time),time,body,cursor.getLong(3),sender));
                }
                scanned++;
                if(batch.size()==500){Store.Batch result=Store.importBatch(c,batch,generation);added+=result.added();duplicates+=result.duplicates();refreshed+=result.refreshed();excluded+=result.excluded();batch.clear();}
            }
        }
        if(!batch.isEmpty()){Store.Batch result=Store.importBatch(c,batch,generation);added+=result.added();duplicates+=result.duplicates();refreshed+=result.refreshed();excluded+=result.excluded();}
        return new Result(scanned,added,duplicates,refreshed,excluded,truncated);
    }
}
