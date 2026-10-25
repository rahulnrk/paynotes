package in.prardhana.app;
import android.Manifest;
import android.app.Notification;
import android.content.pm.PackageManager;
import android.service.notification.*;

public class CaptureService extends NotificationListenerService {
    private static volatile CaptureService connected;
    public record Snapshot(int active, int added, int duplicates, int ignored) {}
    @Override public void onListenerConnected(){connected=this;}
    @Override public void onListenerDisconnected(){connected=null;}
    @Override public void onDestroy(){if(connected==this)connected=null;super.onDestroy();}
    static boolean isConnected(){return connected!=null;}
    static Snapshot importActive() {
        CaptureService service=connected;
        if(service==null)throw new IllegalStateException("Notification access is not connected. Enable access in Android Settings.");
        StatusBarNotification[] events=service.getActiveNotifications();int added=0,duplicates=0,ignored=0;
        if(events==null)return new Snapshot(0,0,0,0);
        for(StatusBarNotification event:events){String result=service.capture(event);if(result.startsWith("Transaction added"))added++;else if(result.equals("Duplicate ignored"))duplicates++;else ignored++;}
        return new Snapshot(events.length,added,duplicates,ignored);
    }
    @Override public void onNotificationPosted(StatusBarNotification event) {
        if(!Store.prefs(this).getBoolean("capture",false)||!SourcePolicy.allowed(event.getPackageName()))return;
        long generation=Store.prefs(this).getLong("generation",0);
        if(!LedgerWork.submit(()->{try{synchronized(Store.class){if(Store.prefs(this).getLong("generation",0)==generation)capture(event);}}catch(RuntimeException e){Store.prefs(this).edit().putBoolean("capture_error",true).apply();}}))Store.prefs(this).edit().putBoolean("capture_error",true).apply();
    }
    private String capture(StatusBarNotification event) {
        String pkg=event.getPackageName();
        if (pkg.equals(getPackageName()) || !Store.prefs(this).getBoolean("capture",false)) return "Ignored";
        Notification n=event.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0) return "Ignored";
        String title=String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE,""));
        CharSequence big=n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String body=String.valueOf(big!=null?big:n.extras.getCharSequence(Notification.EXTRA_TEXT,""));
        boolean liveSms=Store.prefs(this).getBoolean("sms_capture",false)&&checkSelfPermission(Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED;
        // Disallowed apps are dropped before their text reaches events, diagnostics or the pattern lab.
        SourcePolicy.Notice notice=SourcePolicy.notice(pkg,title,body,liveSms);
        if(notice==null)return "Ignored";
        synchronized(Store.class){if(!Store.prefs(this).getBoolean("capture",false))return "Ignored";PatternLab.observeNotification(this,notice.text());return Store.capture(this,notice.text(),pkg,event.getPostTime(),notice.sender(),0);}
    }
}
