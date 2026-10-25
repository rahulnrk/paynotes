package in.prardhana.app;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;
import java.util.*;

/** Regressions for the 25 Oct 2026 MVP audit: F01 amount role, F02 amount correction, F03 event evidence, F05/F06 capture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class AuditFixesTest {
 android.content.Context c;
 static final String SENDER="AX-IDFCFB-S";
 @Before public void setup()throws Exception{c=RuntimeEnvironment.getApplication();reset();Store.prefs(c).edit().clear().putBoolean("sms_capture",true).commit();}
 @After public void cleanup()throws Exception{reset();Store.prefs(c).edit().clear().commit();}
 void reset()throws Exception{var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);LedgerDb db=(LedgerDb)f.get(null);if(db!=null)db.close();f.set(null,null);c.deleteDatabase("ledger.db");}
 JSONObject only(){JSONArray rows=Store.rows(c);assertEquals(1,rows.length());return rows.optJSONObject(0);}

 @Test public void balanceIsNeverTheMovementAmount(){
  assertEquals(50000,TransactionParser.parse("Available balance INR 9000. Your A/c XX1234 debited INR 500 at Cafe ref TEST123456",SENDER).paise());
  assertEquals(50000,TransactionParser.parse("Your A/c XX1234 debited INR 500 at Cafe ref TEST123456. Available balance INR 9000",SENDER).paise());
  assertEquals(75000,TransactionParser.parse("INR 750 spent on Card XX12 at Amazon. Avl Lmt INR 50,000",SENDER).paise());
  assertEquals(24000,TransactionParser.parse("Your IDBI A/c XX12 debited for Rs 240.00; Bal Rs 2,310.45 Example credited. UPI Ref 512345678901",SENDER).paise());
  assertEquals("fee is not the movement",100000,TransactionParser.parse("Rs 1000 debited from A/c XX12 for ATM wdl. Charges Rs 20 applicable",SENDER).paise());
 }
 @Test public void malformedOrAmbiguousAmountsAbstain(){
  assertNull(TransactionParser.parse("Your A/c XX1234 debited INR 10.999 at Cafe ref TEST123456",SENDER));
  assertNull(TransactionParser.parse("Your A/c XX1234 debited INR 500 and INR 700 at Cafe ref TEST123456",SENDER));
  assertNull("only a balance is stated",TransactionParser.parse("Your A/c XX1234 debited. Avl Bal Rs 9000",SENDER));
  Store.capture(c,"Your A/c XX1234 debited INR 10.999 at Cafe ref TEST123456","sms",1000000,SENDER,999000);
  assertEquals(0,Store.rows(c).length());
 }
 @Test public void correctedAmountChangesTotalsAndSurvivesReimport()throws Exception{
  String text="Your A/c XX1234 debited INR 500 at Cafe ref TEST123456";long time=1000000,sent=999000;
  Store.capture(c,text,"sms",time,SENDER,sent);String id=only().getString("id");
  Store.review(c,id,"Cafe","Food & drink","debit",false,45050);
  JSONObject row=only();assertEquals(45050,row.getLong("paise"));assertEquals(50000,row.getLong("originalPaise"));assertEquals("You corrected",row.getString("amountSource"));
  assertEquals(45050,LedgerDb.get(c).outflow(0,2000000).moneyOut());
  // The same SMS imported again, and a parser upgrade, keep the user's amount and the original evidence.
  List<Store.Candidate> again=List.of(new Store.Candidate(TransactionParser.parse(text,SENDER),Store.fingerprint(text,time),time,text,sent,SENDER));
  Store.importBatch(c,again,0);
  row=only();assertEquals(45050,row.getLong("paise"));assertEquals(50000,row.getLong("originalPaise"));
  // Typing the extracted value back removes the override instead of stacking it.
  Store.review(c,id,"Cafe","Food & drink","debit",false,50000);row=only();assertEquals(50000,row.getLong("paise"));assertFalse(row.has("originalPaise"));assertFalse(row.has("amountSource"));
 }
 @Test public void typedAmountsAreValidatedAsIntegerPaise(){
  assertEquals(125050,Store.parseRupees("1,250.50"));assertEquals(1,Store.parseRupees("0.01"));
  assertEquals(-1,Store.parseRupees("10.999"));assertEquals(-1,Store.parseRupees("0"));assertEquals(-1,Store.parseRupees("-5"));assertEquals(-1,Store.parseRupees("abc"));assertEquals(-1,Store.parseRupees("1e12"));
  try{Store.review(c,"missing","x","Other","debit",false,-1);fail();}catch(IllegalArgumentException expected){}
 }
 JSONObject event(String id){JSONArray all=LedgerDb.get(c).events();for(int i=0;i<all.length();i++)if(all.optJSONObject(i).optString("id").equals(id))return all.optJSONObject(i);return null;}
 @Test public void eventResolutionFollowsItsEvidence()throws Exception{
  LedgerDb.get(c).event(new JSONObject().put("id","bill-1").put("fingerprint","bill-1").put("time",900000).put("type","Bill or EMI due").put("reference","TEST123456").put("amount",50000).put("status","Open").put("issuer","").put("account",""));
  Store.capture(c,"Your A/c XX1234 debited INR 500 at Cafe ref TEST123456","sms",1000000,SENDER,999000);String id=only().getString("id");
  assertEquals("Paid / received",event("bill-1").getString("status"));assertEquals(id,event("bill-1").getString("movementId"));
  Store.review(c,id,"Cafe","Food & drink","credit",false);
  assertEquals("money in cannot pay a bill","Open",event("bill-1").getString("status"));assertFalse(event("bill-1").has("movementId"));
  Store.review(c,id,"Cafe","Food & drink","debit",false);assertEquals("Paid / received",event("bill-1").getString("status"));
  Store.review(c,id,"Cafe","Not a transaction","debit",false);assertEquals("Open",event("bill-1").getString("status"));
  Store.review(c,id,"Cafe","Food & drink","debit",false);assertEquals("Paid / received",event("bill-1").getString("status"));
  Store.edit(c,id,"","",true);
  assertEquals("deleted evidence re-opens the event","Open",event("bill-1").getString("status"));
 }
 @Test public void userConfirmedEventStatusSurvivesDeletion()throws Exception{
  LedgerDb.get(c).event(new JSONObject().put("id","bill-2").put("fingerprint","bill-2").put("time",900000).put("type","Bill or EMI due").put("reference","TEST123456").put("amount",50000).put("status","Open").put("issuer","").put("account",""));
  Store.capture(c,"Your A/c XX1234 debited INR 500 at Cafe ref TEST123456","sms",1000000,SENDER,999000);String id=only().getString("id");
  FinancialEvents.resolve(c,"bill-2","Paid / received");Store.edit(c,id,"","",true);
  assertEquals("Paid / received",event("bill-2").getString("status"));assertEquals("You confirmed",event("bill-2").getString("statusSource"));
 }
}
