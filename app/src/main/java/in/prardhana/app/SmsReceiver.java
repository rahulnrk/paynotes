package in.prardhana.app;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** SMS messages are joined before parsing; bodies and sender numbers are never persisted. */
public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()) ||
            !Store.prefs(c).getBoolean("sms_capture", false) ||
            c.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) body.append(part.getMessageBody());
        PendingResult pending = goAsync();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                synchronized(Store.class) {
                    if (Store.prefs(app).getBoolean("sms_capture", false))
                        Store.capture(app, body.toString(), "sms", System.currentTimeMillis(),parts[0].getOriginatingAddress(),parts[0].getTimestampMillis());
                }
            } finally { pending.finish(); }
        }, "sms-extract").start();
    }
}
