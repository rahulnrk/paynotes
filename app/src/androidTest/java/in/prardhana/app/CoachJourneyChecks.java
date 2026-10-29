package in.prardhana.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.time.*;

/** Exercises actual dialogs and persistence on a disposable emulator, never a real device. */
public class CoachJourneyChecks extends PaynotesDesignChecks {
    AccessibilityNodeInfo node(AccessibilityNodeInfo root,String label){
        if(root==null)return null;
        if(label.equalsIgnoreCase(root.getText()==null?"":root.getText().toString())||label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        for(int i=0;i<root.getChildCount();i++){var result=node(root.getChild(i),label);if(result!=null)return result;}return null;
    }
    AccessibilityNodeInfo node(String label)throws Exception{for(int i=0;i<80;i++){var n=node(getUiAutomation().getRootInActiveWindow(),label);if(n!=null)return n;Thread.sleep(100);}throw new AssertionError("Missing dialog control: "+label);}
    void tap(String label)throws Exception{var n=node(label);while(!n.isClickable()&&n.getParent()!=null)n=n.getParent();check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Click "+label);waitForIdleSync();Thread.sleep(300);}
    AccessibilityNodeInfo editable(AccessibilityNodeInfo root,String label){if(root==null)return null;if(root.isEditable()&&label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;for(int i=0;i<root.getChildCount();i++){var n=editable(root.getChild(i),label);if(n!=null)return n;}return null;}
    AccessibilityNodeInfo scrollable(AccessibilityNodeInfo root){if(root==null)return null;if(root.isScrollable())return root;for(int i=0;i<root.getChildCount();i++){var n=scrollable(root.getChild(i));if(n!=null)return n;}return null;}
    void fill(String label,String value)throws Exception{AccessibilityNodeInfo input=null;for(int attempt=0;attempt<10;attempt++){var root=getUiAutomation().getRootInActiveWindow();input=editable(root,label);if(input!=null&&input.isVisibleToUser())break;var scroll=scrollable(root);if(scroll!=null)scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(300);}check(input!=null,"Editable field "+label);Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"Fill "+label);}
    void waitState(String key)throws Exception{for(int i=0;i<100;i++){if(CoachStore.load(LedgerDb.get(getTargetContext())).has(key))return;Thread.sleep(100);}throw new AssertionError("State not saved: "+key);}
    @Override public void onStart(){Bundle result=new Bundle();if(!Build.PRODUCT.startsWith("sdk")&&!Build.MODEL.contains("sdk_gphone")){result.putString("stream","REFUSED: emulator only");finish(0,result);return;}
        try{
            var systemRoot=getUiAutomation().getRootInActiveWindow();
            if(systemRoot!=null&&!systemRoot.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty()){var close=systemRoot.findAccessibilityNodeInfosByText("Close app");if(!close.isEmpty())close.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(1500);}
            seed();screen=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitText("Build my plan");click("Build my plan");
            fill("Take-home pay (₹)","60000");fill("Fixed commitments (₹)","25000");fill("Essential variable costs (₹)","12000");fill("Debt payments (₹)","5000");fill("Irregular costs and buffer (₹)","3000");fill("Goal contribution per month (₹)","5000");fill("Payday (1–31)","1");
            tap("Review plan");shot("coach-01-plan-confirmation");check(!CoachStore.load(LedgerDb.get(getTargetContext())).has("plan"),"Review must not save");tap("Confirm");waitState("plan");waitText("Review plan & goals");shot("coach-02-overview");
            runOnMainSync(()->screen.ui().navigate("Insights"));waitText("My plan");shot("coach-03-insights");click("Goal");fill("What are you saving for?","A weekend away");fill("Goal amount (₹)","6000");fill("Target date (YYYY-MM-DD)",LocalDate.now().plusWeeks(12).toString());tap("Review goal");shot("coach-04-goal-proposal");tap("Confirm");waitState("goal");
            runOnMainSync(()->screen.ui().coach.contribution());fill("Amount (₹)","500");tap("Review");tap("Confirm");waitState("contributions");check(MoneyPlan.funded(CoachStore.load(LedgerDb.get(getTargetContext())))==50000,"Only confirmed funding recorded");
            click("Limits");fill("Monthly limit (₹; 0 removes it)","2500");tap("Review limit");tap("Confirm");waitState("limits");
            runOnMainSync(()->screen.ui().coach.limits());node("Category limits");shot("coach-05-limit-progress");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            // Inject the Android speech result: text must remain an editable draft and not send.
            String before=Store.prefs(getTargetContext()).getString("paynotes_chat","[]");
            runOnMainSync(()->screen.onActivityResult(731,Activity.RESULT_OK,new Intent().putStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS,new java.util.ArrayList<>(java.util.List.of("Help me review my goal")))));
            check(before.equals(Store.prefs(getTargetContext()).getString("paynotes_chat","[]")),"Voice cannot auto-send");waitText("Help me review my goal");shot("coach-06-voice-draft");click("Send message");waitText("LOCAL SUMMARY");shot("coach-07-goal-coaching");
            runOnMainSync(()->screen.ui().coach.meaning(LedgerDb.get(getTargetContext()).byId("design-0")));fill("Your personal share (₹)","60");tap("Review personal share");shot("coach-08-share-confirmation");tap("Confirm");Thread.sleep(500);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            check(CoachStore.snapshot(LedgerDb.get(getTargetContext()),LocalDate.now()).awaiting()==12000,"Shared remainder tracked");
            runOnMainSync(screen::finish);screen=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitText("Review plan & goals");check(MoneyPlan.funded(CoachStore.load(LedgerDb.get(getTargetContext())))==50000,"Funding survives restart");
            result.putBoolean("passed",true);result.putString("stream","PASS: plan review/confirmation, goal affordability, confirmed contribution, category limit, editable voice draft, offline coaching, personal share, persistence. 8 screenshots. Speech hardware and live AI require configured services.\n");
        }catch(Throwable e){try{shot("coach-failure");}catch(Exception ignored){}result.putBoolean("passed",false);result.putString("stream","FAIL: "+e+"\n");android.util.Log.e("CoachJourneyChecks","Failure",e);}finally{if(screen!=null)runOnMainSync(screen::finish);}finish(Activity.RESULT_OK,result);
    }
}
