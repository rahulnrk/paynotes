package in.prardhana.app;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.*;
import org.json.*;
import java.util.*;

/** Run only on a disposable emulator. Restores the app preferences after each check. */
public class PilotInstrumentation extends Instrumentation {
    private String reference;
    @Override public void onCreate(Bundle args){super.onCreate(args);reference=args.getString("reference","PILOTSMS123456");start();}
    void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onStart(){
        if(!android.os.Build.FINGERPRINT.contains("generic")&&!android.os.Build.MODEL.contains("sdk_gphone")&&!android.os.Build.PRODUCT.startsWith("sdk")){Bundle refused=new Bundle();refused.putString("stream","REFUSED: emulator-only fixture checks cannot run on a physical phone.\n");finish(0,refused);return;}
        Context c=getTargetContext();Map<String,?> backup=Store.prefs(c).getAll();Bundle result=new Bundle();
        try{
            Store.prefs(c).edit().clear().putBoolean("sms_capture",true).commit();
            String message="Paid INR 240 to Swiggy ref CHECK123456";
            check(Store.capture(c,message,"sms",12345).startsWith("Transaction added"),"SMS extraction");
            check(Store.capture(c,message,"com.csam.icici.bank.imobile",12346).equals("Duplicate ignored"),"Cross-source duplicate");
            check(Store.capture(c,"Paid INR 240 to Swiggy ref OTHER123456","sms",12347).startsWith("Transaction added"),"Distinct references preserved");
            check(Store.capture(c,"INR 500 debited", "sms",90000).startsWith("Transaction added"),"Unknown merchant persisted");
            check(Store.capture(c,"INR 500 debited", "sms",90000).equals("Duplicate ignored"),"Identical unknown merchant import deduplicates");
            SecureConfig.save(c,"instrumentation-only-token-123456");check(SecureConfig.token(c).equals("instrumentation-only-token-123456"),"Encrypted token round-trip");
            check(!Store.prefs(c).getString("ai_token","").contains("instrumentation-only"),"Token not stored in plaintext");
            InsightEngine.Insight insight=new InsightEngine.Insight("private-id","budget","PRIVATE MERCHANT","PRIVATE SMS","Recorded",Map.of("spentPaise",800L,"budgetPaise",1000L));
            String cloud=Insights.cloudPayload(List.of(insight)).toString();check(!cloud.contains("PRIVATE")&&!cloud.contains("private-id"),"Cloud payload redaction");
            check(Insights.local(c).isEmpty(),"Unreviewed transactions do not create predictions");
            JSONObject protectedRow=Store.rows(c).getJSONObject(0);String protectedId=protectedRow.getString("id");Store.edit(c,protectedId,"Reviewed merchant","Health",false);
            java.util.Map<String,TransactionParser.Parsed> refresh=new java.util.HashMap<>();refresh.put(protectedRow.getString("fingerprint"),null);refresh.put(Store.fingerprint("INR 500 debited",90000),null);Store.refreshImported(c,refresh,0);
            boolean preserved=false,flagged=false;JSONArray refreshed=Store.rows(c);for(int i=0;i<refreshed.length();i++){JSONObject r=refreshed.getJSONObject(i);if(r.optString("id").equals(protectedId))preserved=r.optString("merchant").equals("Reviewed merchant")&&r.optString("category").equals("Health");if(r.optLong("time")==90000)flagged=r.optString("category").equals("Not a transaction");}
            check(preserved&&flagged,"Re-import preserves reviewed edits and retains rejected candidates for review");
            String grammar=MessagePatterns.shape("Received Rs 1234 from Alice example123@ybl account XX9876");check(!grammar.contains("alice")&&!grammar.contains("1234")&&!grammar.contains("9876")&&!grammar.contains("ybl"),"Pattern grammar removes names and identifiers");
            MerchantRules.remember(c,"fixture@upi","Unknown merchant","Fixture shop","Groceries");
            Store.capture(c,"Paid INR 250 to fixture@upi ref FIXTURE1111","manual",System.currentTimeMillis());boolean alias=false;JSONArray aliases=Store.rows(c);for(int i=0;i<aliases.length();i++){JSONObject r=aliases.getJSONObject(i);if(r.optString("vpa").equals("fixture@upi"))alias=r.optString("merchant").equals("Fixture shop")&&r.optString("category").equals("Groceries");}check(alias,"Exact UPI alias applied locally");check(MerchantRules.key("","Unknown merchant").isEmpty(),"Unknown identities cannot share one global alias");
            java.util.List<Store.Candidate> batch=new java.util.ArrayList<>();for(int i=0;i<500;i++){String body="Paid INR 100 to Fixture shop ref BATCHREF"+i;batch.add(new Store.Candidate(TransactionParser.parse(body),Store.fingerprint(body,1000000L+i),1000000L+i));}
            Store.Batch once=Store.importBatch(c,batch,0),twice=Store.importBatch(c,batch,0);check(once.added()==500&&twice.added()==0,"Batched historical import is idempotent and preserves distinct references");
            String bankOnly="Your a/c XX1234 debited INR 350 ref CORRELATE1234";long matchedTime=System.currentTimeMillis();Store.capture(c,bankOnly,"sms",matchedTime);Store.capture(c,"Paid INR 350 to Fixture cafe ref CORRELATE1234","com.google.android.apps.nbu.paisa.user",matchedTime+1);Store.importBatch(c,java.util.List.of(new Store.Candidate(TransactionParser.parse(bankOnly),Store.fingerprint(bankOnly,matchedTime),matchedTime)),0);boolean enriched=false;JSONArray correlated=Store.rows(c);for(int i=0;i<correlated.length();i++){JSONObject row=correlated.getJSONObject(i);if(row.optString("reference").equals("CORRELATE1234"))enriched=row.optString("merchant").equals("Fixture cafe")&&row.optString("nameSource").equals("Matched payment reference");}check(enriched,"Rich notification identity survives bank SMS re-import");
            long priorSpend=Store.spent(c,"Daily");Store.capture(c,"Your electricity bill payment of INR 499 was successful","com.phonepe.app",System.currentTimeMillis());check(Store.spent(c,"Daily")==priorSpend,"Receipt acknowledgement is excluded from spending totals until reviewed");
            int eventCount=FinancialEvents.rows(c).length();String scheduled="SBI UPI AutoPay debit of Rs 399 is scheduled on "+java.time.LocalDate.now().plusDays(1);long eventTime=System.currentTimeMillis();
            Store.capture(c,scheduled,"sms",eventTime,"AD-SBIUPI-S",0);Store.capture(c,scheduled,"sms",eventTime,"AD-SBIUPI-S",0);
            check(FinancialEvents.rows(c).length()==eventCount+1&&Store.spent(c,"Daily")==priorSpend,"Scheduled evidence stored once and excluded from spending");
            check(FinancialEvents.upcoming(c).size()==1,"Upcoming message-stated payment available before review");
            JSONObject groupA=new JSONObject().put("merchant","Fixture group").put("direction","debit").put("source","sms").put("issuer","SBI").put("channel","UPI");JSONObject groupB=new JSONObject(groupA.toString());check(Store.sameReviewGroup(groupA,groupB),"Exact group matches");groupB.put("issuer","Kotak");check(!Store.sameReviewGroup(groupA,groupB),"Group does not cross issuers");groupB.put("issuer","SBI").put("reviewed",true);check(!Store.sameReviewGroup(groupA,groupB),"Reviewed edits protected from group changes");
            Store.capture(c,"Get up to Rs 20000 credited to your account. Apply now","sms",eventTime,"VM-FIXTUR-P",0);check(Store.spent(c,"Daily")==priorSpend,"Promotional source does not enter spending");
            String metadata="IDFC A/c XX123 debited INR 52 on 23Oct26. New Bal :INR -250.50 Refno META123456";Store.capture(c,metadata,"sms",eventTime,"AD-IDFCFB-S",eventTime-1000);boolean facts=false;JSONArray detailed=Store.rows(c);for(int i=0;i<detailed.length();i++){JSONObject row=detailed.getJSONObject(i);if(row.optString("reference").equals("META123456"))facts=row.optLong("reportedBalance")==-25050&&row.optString("messageDate").equals("2026-10-23")&&row.optLong("smsSentAt")==eventTime-1000&&!row.has("body");}check(facts,"Balance and timestamp provenance retained without raw body");
            int movementRows=Store.rows(c).length();long movementSpend=Store.spent(c,"Daily");Store.capture(c,"Paid INR 31 to Fixture ref RECEIPTLINK123","sms",eventTime);Store.capture(c,"Your bill payment of INR 31 was successful ref RECEIPTLINK123","com.phonepe.app",eventTime);check(Store.rows(c).length()==movementRows+2&&Store.spent(c,"Daily")==movementSpend+3100,"Receipt reference cannot demote completed bank movement");
            MainActivity screen=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{screen.overview();screen.activity();screen.insights();screen.smsControls();screen.render();});
            runOnMainSync(screen::finish);
            int before=Store.rows(c).length();
            Bundle waiting=new Bundle();waiting.putString("stream","READY_FOR_SMS "+reference+"\n");sendStatus(1,waiting);
            boolean found=false;
            for(int attempt=0;attempt<600;attempt++){
                JSONArray rows=Store.rows(c);for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("reference").equals(reference))found=true;
                if(found)break;try{Thread.sleep(100);}catch(InterruptedException e){throw new IllegalStateException(e);}
            }
            check(found,"Real SMS broadcast was not captured");check(Store.rows(c).length()==before+1,"Exactly one SMS transaction added");
            int captured=Store.rows(c).length();SmsImport.Result first=SmsImport.recent(c,90,0);int imported=Store.rows(c).length();SmsImport.Result repeated=SmsImport.recent(c,90,0);
            check(Store.rows(c).length()==imported&&repeated.added()==0,"Inbox re-import is idempotent");
            Store.prefs(c).edit().putBoolean("sms_capture",false).commit();boolean cancelled=false;try{SmsImport.recent(c,90,0);}catch(IllegalStateException expected){cancelled=true;}check(cancelled||repeated.scanned()==0,"Disabled capture stops import");
            result.putString("stream","PASS: SMS broadcast, inbox import/re-import, duplicate policy, encrypted token, and cloud redaction. Inbox scanned: "+first.scanned()+"\n");
            result.putBoolean("passed",true);
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");result.putBoolean("passed",false);}
        finally{
            synchronized(Store.class){SharedPreferences.Editor editor=Store.prefs(c).edit().clear();for(var item:backup.entrySet()){Object value=item.getValue();if(value instanceof String)editor.putString(item.getKey(),(String)value);else if(value instanceof Boolean)editor.putBoolean(item.getKey(),(Boolean)value);else if(value instanceof Integer)editor.putInt(item.getKey(),(Integer)value);else if(value instanceof Long)editor.putLong(item.getKey(),(Long)value);else if(value instanceof Float)editor.putFloat(item.getKey(),(Float)value);}editor.commit();}BudgetWidget.refresh(c);
        }
        finish(result.getBoolean("passed")?-1:0,result);
    }
}
