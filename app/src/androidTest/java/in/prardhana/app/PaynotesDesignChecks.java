package in.prardhana.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.io.*;

/** Disposable emulator only. Synthetic fixtures are never packaged in the production APK. */
public class PaynotesDesignChecks extends Instrumentation {
    MainActivity screen;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;if(text.equals(String.valueOf(v.getContentDescription())))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View match=find(g.getChildAt(i),text);if(match!=null)return match;}}return null;}
    View find(String text){return find(screen.getWindow().getDecorView(),text);}
    void click(String text){runOnMainSync(()->{View v=find(text);check(v!=null,"Control missing: "+text);while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();check(v.performClick(),"Control must respond: "+text);});}
    void waitText(String text)throws Exception{for(int i=0;i<100;i++){final boolean[] found={false};runOnMainSync(()->found[0]=find(text)!=null);if(found[0])return;Thread.sleep(100);}throw new AssertionError("Timed out: "+text);}
    void shot(String name)throws Exception{waitForIdleSync();Thread.sleep(1200);var active=getUiAutomation().getRootInActiveWindow();for(int retry=0;retry<20&&(active==null||!"in.prardhana.app".contentEquals(active.getPackageName()));retry++){Thread.sleep(250);active=getUiAutomation().getRootInActiveWindow();}Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot available");File dir=getTargetContext().getExternalFilesDir("design-checks");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();check(active!=null&&"in.prardhana.app".contentEquals(active.getPackageName()),"Unexpected foreground: "+(active==null?"none":active.getPackageName())+" at "+name);}
    void seed()throws Exception{
        Context c=getTargetContext();LedgerDb db=LedgerDb.get(c);db.clear();Store.prefs(c).edit().clear().putString("profile_name","Demo preview").putLong("budget",2500000).putString("period","Monthly").commit();
        String[] names={"Morning coffee","Fresh groceries","Metro commute","Lunch at the cafe","Internet bill","Weekend dinner","Bookshop"};
        String[] categories={"Food & drink","Groceries","Transport","Food & drink","Bills","Food & drink","Shopping"};
        long[] amounts={18000,134500,6000,42000,79900,89000,65000};
        for(int d=0;d<60;d++){
            LocalDate date=LocalDate.now().minusDays(d);int i=d%names.length;
            long time=date.atTime(d==0?0:12,d==0?1:30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            db.put(new JSONObject().put("id","design-"+d).put("time",time).put("paise",amounts[i]+(d%3)*3000).put("merchant",names[i]).put("category",categories[i]).put("direction","debit").put("source","manual").put("issuer",d%3==0?"HDFC":"ICICI").put("account",d%3==0?"XX1842":"XX9031").put("reviewed",true));
        }
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        if(!Build.PRODUCT.startsWith("sdk")&&!Build.MODEL.contains("sdk_gphone")){result.putString("stream","REFUSED: disposable emulator only\n");finish(0,result);return;}
        try{
            // A host sleep can leave the emulator's System UI ANR over every app.
            // Recover only that named system dialog; an app ANR must fail the checks.
            var systemRoot=getUiAutomation().getRootInActiveWindow();
            if(systemRoot!=null&&!systemRoot.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty()){
                var close=systemRoot.findAccessibilityNodeInfosByText("Close app");
                if(!close.isEmpty())close.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                Thread.sleep(1500);
            }
            seed();screen=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitText("Recorded spending");shot("01-overview-week");
            click("Day");waitText("Recorded spending");shot("02-overview-day");
            click("Previous day");waitText("Recorded spending");waitText("Yesterday  ⌄");shot("03-overview-yesterday");
            click("Month");waitText("Recorded spending");shot("04-overview-month");
            click("View payments");waitText("Activity");Thread.sleep(500);shot("05-activity-filtered");
            check(screen.ui().type.equals("Spending"),"Overview drills into spending");check(screen.ui().from>0,"Overview period preserved");
            click("Reset filters");waitText("60 payments · newest first");Thread.sleep(300);runOnMainSync(()->{View amount=find("−"+Store.money(18000));check(amount!=null&&amount.getGlobalVisibleRect(new android.graphics.Rect()),"Payment amount must be visible");});shot("06-activity-all");
            runOnMainSync(()->((EditText)find("Search payments")).setText("no-such-merchant"));waitText("No payments match these filters");shot("07-activity-empty");
            runOnMainSync(()->screen.ui().navigate("Insights"));waitText("How does this month compare?");shot("08-insights-start");
            click("How does this month compare?");waitText("LOCAL SUMMARY");shot("09-insights-local-answer");
            check(Store.prefs(getTargetContext()).getString("paynotes_chat","").contains("last month"),"Local conversation persisted");
            runOnMainSync(()->screen.ui().navigate("Settings"));waitText("Messages & notifications");shot("10-settings");
            click("Messages & notifications");shot("11-connections");
            runOnMainSync(screen::finish);waitForIdleSync();
            LedgerDb.get(getTargetContext()).clear();
            screen=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitText("Your money, in perspective.");shot("12-overview-empty");
            result.putBoolean("passed",true);result.putString("stream","PASS: 4 tabs, day/week/month, yesterday, drill-down, search empty state, local chat, connections and empty ledger. 12 screenshots use fictional fixtures.\n");
        }catch(Throwable e){result.putBoolean("passed",false);result.putString("stream","FAIL: "+e+"\n");android.util.Log.e("PaynotesDesignChecks","Failure",e);}
        finally{if(screen!=null)runOnMainSync(screen::finish);}
        finish(Activity.RESULT_OK,result);
    }
}
