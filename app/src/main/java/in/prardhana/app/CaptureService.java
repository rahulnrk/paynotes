package in.prardhana.app;
import android.app.Notification;
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
        capture(event);
    }
    private String capture(StatusBarNotification event) {
        if (event.getPackageName().equals(getPackageName()) || !Store.prefs(this).getBoolean("capture",false)) return "Ignored";
        Notification n=event.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY)!=0) return "Ignored";
        String title=String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE,""));
        CharSequence big=n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String body=String.valueOf(big!=null?big:n.extras.getCharSequence(Notification.EXTRA_TEXT,""));
        synchronized(Store.class){if(!Store.prefs(this).getBoolean("capture",false))return "Ignored";PatternLab.observeNotification(this,title+"\n"+body);return Store.capture(this,title+"\n"+body,event.getPackageName(),event.getPostTime(),SourcePolicy.smsApp(event.getPackageName())?title:null,0);}
    }
}
