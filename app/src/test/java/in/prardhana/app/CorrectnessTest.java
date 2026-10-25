package in.prardhana.app;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class CorrectnessTest {
 android.content.Context c;
 @Before public void setup()throws Exception{c=RuntimeEnvironment.getApplication();reset();Store.prefs(c).edit().clear().putBoolean("sms_capture",true).commit();}
 @After public void cleanup()throws Exception{reset();Store.prefs(c).edit().clear().commit();}
 void reset()throws Exception{var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);LedgerDb db=(LedgerDb)f.get(null);if(db!=null)db.close();f.set(null,null);c.deleteDatabase("ledger.db");}
 void reopen()throws Exception{var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);((LedgerDb)f.get(null)).close();f.set(null,null);}
 @Test public void atmAndUpiNeverCollapse(){
  String atm="Your Account XXXX1234 had a ATM WDL of RS. 500.00 DR on 24/10/26 at 10:15:30 Current balance is Rs. 2,310.45 CR TGB BRANCH";
  String upi="Your a/c no. XXXXXXXX1234 is debited for Rs.500.00 on 24-10-26 and credited to a/c no. XXXXXXXX0025 (UPI Ref no 512345678901)-TGB";
  Store.capture(c,atm,"sms",1000000,"JX-TGBTXN-S",999000);Store.capture(c,upi,"sms",1060000,"JX-TGBEBK-S",1059000);
  assertEquals(2,Store.rows(c).length());assertEquals(100000,LedgerDb.get(c).outflow(0,2000000).moneyOut());
 }
 @Test public void repeatedNotificationsAreNotProofOfDuplicate(){
  String b="Your A/c XX1234 debited INR 100";
  Store.capture(c,b,"com.google.android.apps.messaging",1000000,"AX-IDFCFB-S",0);Store.capture(c,b,"com.google.android.apps.messaging",1120000,"AX-IDFCFB-S",0);
  assertEquals(2,Store.rows(c).length());
 }
 @Test public void equalArrivalTimesDoNotOverrideDifferentNetworkIdentities(){
  String b="Your IDFC A/c XX1234 debited INR 100";
  Store.capture(c,b,"sms",1000000,"AX-IDFCFB-S",998000);Store.capture(c,b,"sms",1000000,"AX-IDFCFB-S",999000);
  assertEquals(2,Store.rows(c).length());
  Store.capture(c,b,"sms",1000000,"AX-IDFCFB-S",999000);assertEquals(2,Store.rows(c).length());
 }
 @Test public void crossSourceCopyIsOneToOneAndBankScoped(){
  String b="Your A/c XX1234 debited INR 100";
  Store.capture(c,b,"sms",1000000,"AX-IDFCFB-S",999000);
  Store.capture(c,b,"com.google.android.apps.messaging",1001000,"AX-HDFCBK-S",0);assertEquals(2,Store.rows(c).length());
  Store.capture(c,b,"com.google.android.apps.messaging",1002000,"AX-IDFCFB-S",0);assertEquals(2,Store.rows(c).length());
  Store.capture(c,b,"com.google.android.apps.messaging",1003000,"AX-IDFCFB-S",0);assertEquals("second notification is not swallowed by same SMS",3,Store.rows(c).length());
 }
 @Test public void deletedSmsStaysDeletedAfterReopenAndChangedArrival()throws Exception{
  String b="Your IDFC A/c XX1234 debited INR 100 ref DELETE12345";
  Store.capture(c,b,"sms",1000000,"AX-IDFCFB-S",999000);Store.edit(c,Store.rows(c).getJSONObject(0).getString("id"),"","",true);reopen();
  Store.importBatch(c,List.of(new Store.Candidate(TransactionParser.parse(b,"AX-IDFCFB-S"),Store.fingerprint(b,1000500),1000500,b,999000,"AX-IDFCFB-S")),0);
  assertEquals(0,Store.rows(c).length());assertEquals("Deleted entry ignored",Store.capture(c,b,"sms",1000800,"AX-IDFCFB-S",999000));
  LedgerDb.get(c).clear();assertTrue(Store.capture(c,b,"sms",1000800,"AX-IDFCFB-S",999000).startsWith("Transaction added"));
 }
 @Test public void outflowSeparatesCashAndMarkedTransfers()throws Exception{
  LedgerDb db=LedgerDb.get(c);
  for(String category:List.of("Other","Cash withdrawal","Transfer","Payment receipt","Not a transaction"))db.put(new JSONObject().put("id",category).put("source","sms").put("time",1000).put("paise",10000).put("direction","debit").put("category",category));
  LedgerDb.Outflow o=db.outflow(0,2000);assertEquals(30000,o.moneyOut());assertEquals(10000,o.cash());assertEquals(10000,o.transfers());assertEquals(10000,o.remaining());assertEquals(o.remaining(),db.spent(0,2000));
 }
 @Test public void versionOneUpgradeRetainsRowsAndCreatesDeletionMarkers()throws Exception{
  Store.capture(c,"Paid INR 100 to Fixture","manual",1000);LedgerDb db=LedgerDb.get(c);db.getWritableDatabase().execSQL("DROP TABLE tombstones");db.getWritableDatabase().setVersion(1);reopen();
  assertEquals(1,Store.rows(c).length());Store.edit(c,Store.rows(c).getJSONObject(0).getString("id"),"","",true);assertTrue(LedgerDb.get(c).deleted(Store.fingerprint("Paid INR 100 to Fixture",1000)));
 }
 @Test public void reviewingOneRowDoesNotRewriteUnrelatedRows()throws Exception{
  Store.capture(c,"Paid INR 100 to Alpha ref EDIT12345","manual",1000);Store.capture(c,"Paid INR 200 to Beta ref EDIT54321","manual",2000);
  LedgerDb db=LedgerDb.get(c);JSONObject a=Store.rows(c).getJSONObject(0),b=Store.rows(c).getJSONObject(1);
  db.getWritableDatabase().execSQL("CREATE TRIGGER protect_other BEFORE INSERT ON movements WHEN NEW.id='"+b.getString("id")+"' BEGIN SELECT RAISE(ABORT,'Unrelated row rewritten'); END");
  Store.review(c,a.getString("id"),"My Alpha","Health","debit",false);assertEquals("My Alpha",db.byId(a.getString("id")).getString("merchant"));assertEquals(b.toString(),db.byId(b.getString("id")).toString());
 }
 @Test public void monthlyHistoryIncludesUnreviewedAndExcludesReceiptsAndSamples()throws Exception{
  LedgerDb db=LedgerDb.get(c);
  db.put(new JSONObject().put("id","in").put("source","sms").put("time",1000).put("paise",50000).put("direction","credit").put("category","Money received"));
  db.put(new JSONObject().put("id","out").put("source","sms").put("time",1001).put("paise",10000).put("direction","debit").put("category","Other"));
  db.put(new JSONObject().put("id","receipt").put("source","sms").put("time",1002).put("paise",10000).put("direction","debit").put("category","Payment receipt"));
  db.put(new JSONObject().put("id","sample").put("source","sample").put("time",1003).put("paise",90000).put("direction","credit").put("category","Income"));
  LedgerDb.MonthSummary month=db.month(1000,2000);assertEquals(50000,month.moneyIn());assertEquals(10000,month.outflow().moneyOut());assertEquals(2,month.entries());assertEquals(2,month.unreviewed());
  assertEquals("in",db.priorityReview(1000,2000).getJSONObject(0).getString("id"));assertEquals(0,db.month(2001,3000).entries());
 }
 @Test public void twentyThousandRowsSingleReviewAndHistory()throws Exception{
  LedgerDb db=LedgerDb.get(c);var sql=db.getWritableDatabase();sql.beginTransaction();
  try{for(int i=0;i<20000;i++)db.put(new JSONObject().put("id","scale-"+i).put("source","sms").put("time",1000+i).put("paise",10000+i).put("direction","debit").put("merchant","Fixture "+i).put("category","Other"));sql.setTransactionSuccessful();}finally{sql.endTransaction();}
  long before=System.nanoTime();Store.review(c,"scale-0","Confirmed","Health","debit",false);LedgerDb.MonthSummary summary=db.month(0,30000);long elapsed=(System.nanoTime()-before)/1000000;
  assertEquals(20000,summary.entries());assertEquals(19999,summary.unreviewed());assertEquals(399990000L,summary.outflow().moneyOut());assertEquals(5,db.priorityReview(0,30000).length());
  System.out.println("20,000-row host simulation: single review + month aggregate "+elapsed+"ms (not a handset benchmark)");
 }
}
