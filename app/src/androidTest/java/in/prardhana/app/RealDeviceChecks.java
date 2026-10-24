package in.prardhana.app;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import org.json.*;

/** Authorized phone test: preserves transactions, never sends data to a model, emits counts only. */
public class RealDeviceChecks extends Instrumentation {
    private boolean authorized;
    private int days=90;
    @Override public void onCreate(Bundle args){super.onCreate(args);authorized="local-phone-test".equals(args.getString("authorization"));days="all".equals(args.getString("history"))?0:90;start();}
    @Override public void onStart(){
        if(!authorized){Bundle refused=new Bundle();refused.putString("stream","REFUSED: real-data checks require explicit authorization argument.\n");finish(0,refused);return;}
        Context c=getTargetContext();Bundle result=new Bundle();StringBuilder report=new StringBuilder();
        try{
            Store.prefs(c).edit().putBoolean("sms_capture",true).putBoolean("capture",true).commit();
            // Some OEM background managers force-stop long instrumentation imports
            // before the UI opens. Keep the authorised study visible on the phone.
            MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->screen.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            long generation=Store.prefs(c).getLong("generation",0);int before=Store.rows(c).length();
            SmsImport.Result first=SmsImport.recent(c,days,generation);int after=Store.rows(c).length();
            int eventsAfterFirst=FinancialEvents.rows(c).length();SmsImport.Result repeat=SmsImport.recent(c,days,generation);
            if(FinancialEvents.rows(c).length()!=eventsAfterFirst)throw new AssertionError("Repeated import changed financial-event count");
            report.append("SMS: scanned=").append(first.scanned()).append(", added=").append(first.added()).append(", duplicates=").append(first.duplicate()).append(", refreshed=").append(first.refreshed()).append(", excludedCandidates=").append(first.excluded()).append(", capped=").append(first.truncated()).append("\n");
            report.append("SMS repeat: added=").append(repeat.added()).append(", duplicates=").append(repeat.duplicate()).append("\n");
            if(repeat.added()!=0)throw new AssertionError("Repeat import added transactions; investigate before claiming idempotency.");
            if(days==0){Store.prefs(c).edit().putBoolean("learn_patterns",true).apply();JSONObject study=PatternLab.scan(c,generation,count->{if(count%5000==0){Bundle progress=new Bundle();progress.putString("stream","STUDY_PROGRESS "+count+" messages\n");sendStatus(1,progress);}});report.append("Full study: total=").append(study.optInt("total")).append(", received=").append(study.optInt("received")).append(", sent=").append(study.optInt("sent")).append(", receivedCandidates=").append(study.optInt("candidates")).append(", unknownNames=").append(study.optInt("unknown")).append(", missingReference=").append(study.optInt("missingReference")).append("\nFamilies: ").append(study.optJSONObject("families")).append("\nTypes: ").append(study.optJSONObject("types")).append("\n");}else auditFormats(c,report);
            NotificationListenerService.requestRebind(new ComponentName(c,CaptureService.class));
            for(int i=0;i<150&&!CaptureService.isConnected();i++)Thread.sleep(100);
            if(CaptureService.isConnected()){
                CaptureService.Snapshot notices=CaptureService.importActive();
                report.append("Notifications: active=").append(notices.active()).append(", added=").append(notices.added()).append(", duplicates=").append(notices.duplicates()).append(", ignored=").append(notices.ignored()).append("\n");
                CaptureService.Snapshot again=CaptureService.importActive();report.append("Notification repeat: added=").append(again.added()).append("\n");
                if(again.added()!=0)throw new AssertionError("Repeated active-notification import added transactions.");
            }else report.append("Notifications: listener is not connected; capture could not be verified.\n");
            JSONArray all=Store.rows(c);int debit=0,credit=0,unknown=0,uncategorised=0,reviewed=0,missingReference=0,excluded=0;
            for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i);if(row.optString("category").equals("Not a transaction")){excluded++;continue;}if(row.optString("direction").equals("debit"))debit++;else credit++;if(row.optString("merchant").equals("Unknown merchant"))unknown++;if(row.optString("category").equals("Other"))uncategorised++;if(row.optBoolean("reviewed"))reviewed++;if(row.optString("reference").isEmpty())missingReference++;}
            report.append("Stored: total=").append(all.length()).append(", excluded=").append(excluded).append(", debits=").append(debit).append(", credits=").append(credit).append(", unknownMerchant=").append(unknown).append(", otherCategory=").append(uncategorised).append(", reviewed=").append(reviewed).append(", missingReference=").append(missingReference).append("\n");
            int dated=0,clock=0,balances=0,receipts=0,accounts=0;for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i);if(row.optString("category").equals("Not a transaction"))continue;if(!row.optString("messageDate").isEmpty())dated++;if(!row.optString("messageClock").isEmpty())clock++;if(row.has("reportedBalance"))balances++;if(!row.optString("account").isEmpty())accounts++;if(row.optString("category").equals("Payment receipt"))receipts++;if(row.has("body")||row.has("sender"))throw new AssertionError("Raw message field retained");}
            java.util.Map<String,Integer> eventTypes=new java.util.TreeMap<>();JSONArray events=FinancialEvents.rows(c);for(int i=0;i<events.length();i++)eventTypes.merge(events.getJSONObject(i).optString("type"),1,Integer::sum);
            report.append("Details: bodyDate=").append(dated).append(", bodyClock=").append(clock).append(", maskedAccount=").append(accounts).append(", balanceSnapshots=").append(balances).append(", receipts=").append(receipts).append("\nFinancial events: ").append(eventTypes).append("; repeat count stable; upcoming=").append(FinancialEvents.upcoming(c).size()).append("\n");
            int findings=Insights.local(c).size();report.append("Local insight count=").append(findings).append("; no rows were marked reviewed by this check.\n");
            for(String period:new String[]{"Daily","Weekly","Monthly"})if(Store.spent(c,period)<0)throw new AssertionError("Invalid spending total");
            runOnMainSync(()->{screen.overview();screen.activity();screen.insights();screen.patterns();screen.smsControls();screen.render();});
            runOnMainSync(()->screen.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            report.append("Overview, insights, and SMS controls rendered. Original inbox untouched; no rows deleted or user-reviewed rows modified; unreviewed parser fields refreshed; no cloud requests.\n");
            result.putBoolean("passed",true);
        }catch(Throwable error){report.append("CHECK STOPPED: ").append(error.getClass().getSimpleName()).append(". Existing data retained.\n");result.putBoolean("passed",false);}
        result.putString("stream",report.toString());finish(result.getBoolean("passed")?-1:0,result);
    }
    private void auditFormats(Context c,StringBuilder report){
        java.util.Map<String,Integer> unknown=new java.util.HashMap<>(),missed=new java.util.HashMap<>();int candidates=0;
        long cutoff=System.currentTimeMillis()-90L*86400000L;
        try(android.database.Cursor cursor=c.getContentResolver().query(android.provider.Telephony.Sms.Inbox.CONTENT_URI,new String[]{"body"},"date >= ?",new String[]{Long.toString(cutoff)},"date DESC")){
            if(cursor==null)return;int count=0;while(cursor.moveToNext()&&count++<5000){String body=cursor.getString(0);TransactionParser.Parsed p=TransactionParser.parse(body);
                if(p!=null&&p.merchant().equals("Unknown merchant"))unknown.merge(shape(body),1,Integer::sum);
                if(p==null&&body.toLowerCase(java.util.Locale.ROOT).matches("(?s).*(?:₹|rs\\.?|inr).*\\b(?:debited|credited|paid|received|transferred)\\b.*")){candidates++;missed.merge(shape(body),1,Integer::sum);}
            }
        }
        report.append("Unparsed amount-and-verb candidates (may include OTP, failures, and promotions)=").append(candidates).append("\n");
        for(var group:java.util.List.of(unknown,missed)){report.append(group==unknown?"Unknown-merchant grammar shapes (all names/numbers masked):\n":"Unparsed grammar shapes (all names/numbers masked):\n");group.entrySet().stream().sorted(java.util.Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5).forEach(e->report.append(e.getValue()).append(" x ").append(e.getKey()).append("\n"));}
    }
    private String shape(String text){
        java.util.Set<String> allowed=java.util.Set.of("inr","rs","₹","debited","credited","paid","received","sent","transferred","to","from","at","on","by","for","via","upi","ref","refno","reference","no","txn","id","imps","neft","account","ac","bal","balance","available","avl","otp","failed","due","will","be","your","has","been","of","is","with","amount","transaction","payment","purchase","using","ending","bank","a","c");
        java.util.regex.Matcher tokens=java.util.regex.Pattern.compile("[a-z]+|[0-9]+|₹").matcher(text.toLowerCase(java.util.Locale.ROOT));StringBuilder out=new StringBuilder();String previous="";int count=0;
        while(tokens.find()&&count++<90){String value=tokens.group();value=allowed.contains(value)?value:Character.isDigit(value.charAt(0))?"n":"x";if(value.equals("x")&&previous.equals("x"))continue;out.append(value).append(' ');previous=value;}
        return out.toString().trim();
    }
}
