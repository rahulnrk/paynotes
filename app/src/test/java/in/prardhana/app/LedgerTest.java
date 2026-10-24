package in.prardhana.app;
import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class LedgerTest {
 Context c;
 @Before public void setup()throws Exception{c=RuntimeEnvironment.getApplication();reset();Store.prefs(c).edit().clear().putBoolean("sms_capture",true).commit();}
 @After public void cleanup()throws Exception{reset();Store.prefs(c).edit().clear().commit();}
 void reset()throws Exception{var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);LedgerDb db=(LedgerDb)f.get(null);if(db!=null)db.close();f.set(null,null);c.deleteDatabase("ledger.db");}
 @Test public void migrationPreservesReviewAndCanReopen()throws Exception{
  JSONObject legacy=new JSONObject().put("id","old").put("fingerprint","legacy-hash").put("source","sms").put("time",1000).put("paise",12345).put("direction","debit").put("merchant","My corrected shop").put("category","Health").put("reviewed",true);
  Store.prefs(c).edit().putString("transactions",new JSONArray().put(legacy).toString()).commit();assertEquals(1,Store.rows(c).length());assertTrue(Store.rows(c).getJSONObject(0).getBoolean("reviewed"));assertFalse(Store.prefs(c).contains("transactions"));
  var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);((LedgerDb)f.get(null)).close();f.set(null,null);assertEquals("My corrected shop",Store.rows(c).getJSONObject(0).getString("merchant"));
 }
 @Test public void malformedMigrationRollsBackAndKeepsOriginal()throws Exception{
  String broken="[{\"id\":\"good\",\"paise\":100},{\"paise\":200}]";Store.prefs(c).edit().putString("transactions",broken).commit();try{Store.rows(c);fail("Must refuse corrupt identity");}catch(IllegalStateException expected){assertEquals(broken,Store.prefs(c).getString("transactions",""));}
 }
 @Test public void liveThenImportNamelessSmsIsOneMovement()throws Exception{
  String body="Your IDFC A/c XX1234 debited INR 100. New Bal INR 200";long network=1000000L;
  assertTrue(Store.capture(c,body,"sms",network+1100,"AX-IDFCFB-S",network).startsWith("Transaction added"));
  var candidate=new Store.Candidate(TransactionParser.parse(body,"AX-IDFCFB-S"),Store.fingerprint(body,network+1500),network+1500,body,network,"AX-IDFCFB-S");
  assertEquals(0,Store.importBatch(c,List.of(candidate),0).added());assertEquals(1,Store.rows(c).length());assertEquals(0,Store.importBatch(c,List.of(candidate),0).added());
 }
 @Test public void twoGenuineFastPaymentsSurvive()throws Exception{
  String body="Paid INR 100 to Cafe";Store.capture(c,body,"sms",10000,"AX-IDFCFB-S",9000);Store.capture(c,body,"sms",11000,"AX-IDFCFB-S",10000);assertEquals(2,Store.rows(c).length());
 }
 @Test public void sameReferenceDifferentAccountsIsNotMerged()throws Exception{
  Store.capture(c,"IDFC A/c XX1234 debited INR 100 ref SAME123456","sms",10000,"AX-IDFCFB-S",9000);
  Store.capture(c,"IDFC A/c XX5678 debited INR 100 ref SAME123456","sms",11000,"AX-IDFCFB-S",10000);assertEquals(2,Store.rows(c).length());
 }
 @Test public void wordReferenceDoesNotMergeDifferentPayments()throws Exception{
  String a="A/c XX1234 Debited with Rs. 100.00 on 26-06-20 by UCO-UPI.Avl Bal Rs.9.10.For disputed txn reporting,click https://example.invalid/x";
  String b="A/c XX1234 Debited with Rs. 100.00 on 09-07-20 by UCO-UPI.Avl Bal Rs.4.60.For disputed txn reporting,click https://example.invalid/x";
  Store.capture(c,a,"sms",1000000,"JD-UCOBNK",999000);Store.capture(c,b,"sms",1000000+13*86400000L,"JD-UCOBNK",999000+13*86400000L);assertEquals(2,Store.rows(c).length());
 }
 @Test public void upiAppHeaderAndBankAlertWithOneRrnAreOneCredit()throws Exception{
  Store.capture(c,"Dear SBI User, your A/c X8080-credited by Rs.300 on 08Jan25 transfer from SAMPLE PAYER Ref No 500812345678 -SBI","sms",1000000,"AD-SBIUPI-S",999000);
  Store.capture(c,"Dear Customer, Acct XX080 is credited with Rs 300.00 on 08-Jan-25 from SAMPLE PAYER. UPI:500812345678 Avl Bal Rs 1,000.00-IDBI Bank","sms",1003000,"VM-IDBIBK",1002000);
  assertEquals(1,Store.rows(c).length());
  Store.capture(c,"Dear SBI User, your A/c X2549-credited by Rs.300 on 08Jan25 transfer from SAMPLE PAYER Ref No 500899999999 -SBI","sms",2000000,"AD-SBIUPI-S",1999000);
  Store.capture(c,"Dear Customer, Acct XX080 is credited with Rs 300.00 on 08-Jan-25 from SAMPLE PAYER. UPI:500899999999 Avl Bal Rs 1,300.00-IDBI Bank","sms",2003000,"VM-IDBIBK",2002000);
  assertEquals("different accounts keep both credits",3,Store.rows(c).length());
 }
 @Test public void tgbLedgerCopyPairsWithItsUpiAlertInEitherOrder()throws Exception{
  String upi="Your a/c no. XXXXXXXX1234 is debited for Rs.500.00 on 12-10-25 and credited to a/c no. XXXXXXXX0025 (UPI Ref no 512345678901)-TGB";
  String ledger="Your Account XXXX1234 had a TO TRANSFER of RS. 500.00 DR on 12/10/25 at 10:15:30Current balance is Rs. 2,310.45 CR TGB BRANCH";
  Store.capture(c,ledger,"sms",1000000,"JX-TGBTXN-S",999000);Store.capture(c,upi,"sms",1060000,"JX-TGBEBK-S",1059000);
  assertEquals(1,Store.rows(c).length());assertEquals("To a/c ••0025",Store.rows(c).getJSONObject(0).getString("merchant"));assertEquals("512345678901",Store.rows(c).getJSONObject(0).getString("reference"));
  // A second genuine ₹500 payment minutes later keeps its own pair; re-import of the ledger copy changes nothing.
  String upi2=upi.replace("512345678901","512345678902"),ledger2=ledger.replace("2,310.45","1,810.45");
  var batch=List.of(new Store.Candidate(TransactionParser.parse(upi2,"JX-TGBEBK-S"),Store.fingerprint(upi2,1300000),1300000,upi2,1299000,"JX-TGBEBK-S"),new Store.Candidate(TransactionParser.parse(ledger2,"JX-TGBTXN-S"),Store.fingerprint(ledger2,1320000),1320000,ledger2,1319000,"JX-TGBTXN-S"),new Store.Candidate(TransactionParser.parse(ledger,"JX-TGBTXN-S"),Store.fingerprint(ledger,1000000),1000000,ledger,999000,"JX-TGBTXN-S"));
  Store.importBatch(c,batch,0);assertEquals(2,Store.rows(c).length());
  for(int i=0;i<2;i++)assertEquals("To a/c ••0025",Store.rows(c).getJSONObject(i).getString("merchant"));
 }
 @Test public void importRepairsRowsStoredByOlderParsers()throws Exception{
  LedgerDb db=LedgerDb.get(c);
  // Parser 6 stored the TGB UPI alert without a bank and named it "and"; the ledger copy was ignored.
  String upi="Your a/c no. XXXXXXXX1234 is debited for Rs.500.00 on 12-10-25 and credited to a/c no. XXXXXXXX0025 (UPI Ref no 512345678901)-TGB";
  String ledger="Your Account XXXX1234 had a TO TRANSFER of RS. 500.00 DR on 12/10/25 at 10:15:30Current balance is Rs. 2,310.45 CR TGB BRANCH";
  db.put(new JSONObject().put("id","old-upi").put("fingerprint",Store.fingerprint(upi,1000000)).put("time",1000000).put("source","sms").put("paise",50000).put("direction","debit").put("merchant","and").put("account","Account ••1234").put("issuer","").put("reference","512345678901").put("reviewed",false));
  // One UPI RRN stored as an SBIUPI credit and an IDBI credit.
  db.put(new JSONObject().put("id","sbi").put("fingerprint","sbi-hash").put("time",2000000).put("source","sms").put("paise",30000).put("direction","credit").put("merchant","SAMPLE PAYER").put("account","Account ••8080").put("issuer","SBI").put("reference","500812345678").put("reviewed",false));
  db.put(new JSONObject().put("id","idbi").put("fingerprint","idbi-hash").put("time",2003000).put("source","sms").put("paise",30000).put("direction","credit").put("merchant","SAMPLE PAYER").put("account","Account ••080").put("issuer","IDBI").put("reference","500812345678").put("reviewed",false));
  // A word reference swallowed a different payment of the same amount.
  String early="A/c XX1234 Debited with Rs. 100.00 on 26-06-20 by UCO-UPI.Avl Bal Rs.9.10.For disputed txn reporting,click https://example.invalid/x";
  db.put(new JSONObject().put("id","late").put("fingerprint","late-hash").put("time",5000000).put("source","sms").put("paise",10000).put("direction","debit").put("merchant","Unknown merchant").put("reference","REPORTING").put("reviewed",false));
  db.alias(Store.fingerprint(early,4000000),"late");
  var batch=List.of(new Store.Candidate(TransactionParser.parse(ledger,"JX-TGBTXN-S"),Store.fingerprint(ledger,1060000),1060000,ledger,1059000,"JX-TGBTXN-S"),
    new Store.Candidate(TransactionParser.parse(upi,"JX-TGBEBK-S"),Store.fingerprint(upi,1000000),1000000,upi,999000,"JX-TGBEBK-S"),
    new Store.Candidate(TransactionParser.parse(early,"JD-UCOBNK"),Store.fingerprint(early,4000000),4000000,early,3999000,"JD-UCOBNK"));
  Store.importBatch(c,batch,0);
  JSONArray rows=Store.rows(c);Map<String,Integer> byAmount=new HashMap<>();for(int i=0;i<rows.length();i++)byAmount.merge(rows.getJSONObject(i).getLong("paise")+rows.getJSONObject(i).getString("direction"),1,Integer::sum);
  assertEquals("TGB pair folded into one debit",Integer.valueOf(1),byAmount.get("50000debit"));
  assertEquals("one RRN, one credit",Integer.valueOf(1),byAmount.get("30000credit"));
  assertEquals("swallowed payment restored",Integer.valueOf(2),byAmount.get("10000debit"));
  assertEquals("To a/c ••0025",db.byHash(Store.fingerprint(upi,1000000)).getString("merchant"));
  assertEquals(0,Store.importBatch(c,batch,0).added());assertEquals(rows.length(),Store.rows(c).length());
 }
 @Test public void eventLiveImportAndUserStatusAreStable()throws Exception{
  String b="SBI A/c XX1234 AutoPay Rs 100 scheduled on 25/10/2026";FinancialEvents.observe(c,b,"AD-SBIUPI-S","sms",10000,9000);FinancialEvents.observe(c,b,"AD-SBIUPI-S","sms",10100,9000);assertEquals(1,FinancialEvents.rows(c).length());String id=FinancialEvents.rows(c).getJSONObject(0).getString("id");FinancialEvents.resolve(c,id,"Cancelled");FinancialEvents.observe(c,b,"AD-SBIUPI-S","sms",10100,9000);assertEquals("Cancelled",FinancialEvents.rows(c).getJSONObject(0).getString("status"));
 }
 @Test public void diagnosticConsentAndReplayCounts()throws Exception{
  String b="Paid INR 100 to SECRET_MERCHANT ref SECRET123456";Learning.observe(c,b,"AX-IDFCFB-S",TransactionParser.parse(b),"same");assertEquals(0,Learning.snapshot(c).getJSONArray("patterns").length());
  Store.prefs(c).edit().putBoolean("pilot_consent",true).commit();Learning.observe(c,b,"AX-IDFCFB-S",TransactionParser.parse(b),"same");Learning.observe(c,b,"AX-IDFCFB-S",TransactionParser.parse(b),"same");JSONObject payload=Learning.snapshot(c);assertEquals(1,payload.getJSONArray("patterns").getJSONObject(0).getInt("count"));assertFalse(payload.toString().contains("SECRET"));PilotSync.pause(c);Learning.observe(c,b,"AX-IDFCFB-S",TransactionParser.parse(b),"another");assertEquals(1,Learning.snapshot(c).getJSONArray("patterns").getJSONObject(0).getInt("count"));
 }
 @Test public void searchEscapesWildcardsAndClearDeletesAll()throws Exception{
  Store.capture(c,"Paid INR 100 to Cafe ref SEARCH12345","manual",10000);assertEquals(1,LedgerDb.get(c).search("All","Cafe",10).length());assertEquals(0,LedgerDb.get(c).search("All","%",10).length());LedgerDb.get(c).clear();assertEquals(0,Store.rows(c).length());
 }
 @Test public void largeBatchPreservesRowsAndReviewedCorrections()throws Exception{
  long started=System.nanoTime();for(int batch=0;batch<4;batch++){List<Store.Candidate> input=new ArrayList<>();for(int i=0;i<500;i++){long t=1000000+batch*500+i;String b="Paid INR 100 to Fixture ref LOADREF"+t;input.add(new Store.Candidate(TransactionParser.parse(b),Store.fingerprint(b,t),t,b,t,"AD-IDFCFB-S"));}assertEquals(500,Store.importBatch(c,input,0).added());assertEquals(0,Store.importBatch(c,input,0).added());}assertEquals(2000,Store.rows(c).length());JSONObject first=Store.rows(c).getJSONObject(0);Store.review(c,first.getString("id"),"My edit","Health","debit",false);Store.importBatch(c,List.of(new Store.Candidate(TransactionParser.parse("Paid INR 100 to Fixture ref LOADREF1000000"),first.getString("fingerprint"),1000000)),0);assertEquals("My edit",LedgerDb.get(c).byHash(first.getString("fingerprint")).getString("merchant"));System.out.println("2000 rows + repeat batches (host simulation): "+((System.nanoTime()-started)/1000000)+"ms");
 }
 @Test public void screenEntryPointsRender()throws Exception{
  MainActivity screen=Robolectric.buildActivity(MainActivity.class).create().start().resume().get();var ready=MainActivity.class.getDeclaredField("ledgerReady");ready.setAccessible(true);for(int wait=0;wait<200&&!ready.getBoolean(screen);wait++){Thread.sleep(10);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}assertTrue(ready.getBoolean(screen));screen.activity();screen.insights();screen.commitments();screen.paymentChecks();screen.pilotControls();screen.reportedBalances();screen.finish();
 }
 @Test public void notificationCreditsRequireSupportedSource(){
  assertNull(SourcePolicy.parse("INR 500 credited to your account",null,"com.unknown.app"));assertNotNull(SourcePolicy.parse("INR 500 credited to your account","AD-IDFCFB-S","com.google.android.apps.messaging"));
 }
 static final String MESSAGES="com.google.android.apps.messaging",UCO_ALERT="A/c XX1234 Debited with Rs.250.00 on 24-10-26 by UCO-UPI.Avl Bal Rs.900.00 Report Dispute https://example.invalid/d";
 @Test public void notificationsFromChatAndUnknownAppsNeverCreateMoney(){
  String chat="Paid Rs 500 to Ravi ref 512345678901";
  assertNull("chat app",SourcePolicy.notice("com.whatsapp","Ravi",chat,false));assertNull(SourcePolicy.parse(chat,null,"com.whatsapp"));
  assertNull("unknown app",SourcePolicy.notice("com.example.shop","Order paid",chat,false));
  assertEquals("UPI app","Payment sent\nPaid Rs 500 to Ravi",SourcePolicy.notice("com.phonepe.app","Payment sent","Paid Rs 500 to Ravi",false).text());
  assertNotNull(SourcePolicy.parse("Paid Rs 500 to Ravi ref 512345678901",null,"com.phonepe.app"));
  assertNull("contact name in an SMS app is a person",SourcePolicy.notice(MESSAGES,"Ravi Kumar",UCO_ALERT,false));
  assertNull("phone number",SourcePolicy.notice(MESSAGES,"+91 98765 43210",UCO_ALERT,false));
  assertNull("six capital letters that are not a bank header",SourcePolicy.notice(MESSAGES,"RAJESH",UCO_ALERT,false));
  SourcePolicy.Notice bank=SourcePolicy.notice(MESSAGES,"JD-UCOBNK-S",UCO_ALERT,false);assertEquals("body only",UCO_ALERT,bank.text());assertEquals("JD-UCOBNK-S",bank.sender());
  assertEquals("bare bank header","XX-UCOBNK",SourcePolicy.notice(MESSAGES,"UCOBNK",UCO_ALERT,false).sender());
  assertNull("live SMS capture already records it",SourcePolicy.notice(MESSAGES,"JD-UCOBNK-S",UCO_ALERT,true));
  assertNull("promotional header",SourcePolicy.parse("Paid Rs 500 to Ravi","VM-OFFERS-P",MESSAGES));
  // Disallowed sources leave no trace: no transaction and no event.
  assertTrue(Store.capture(c,"Your EMI of Rs 5000 is due on 30-10-2026. "+chat,"com.whatsapp",1000).startsWith("No completed"));assertEquals(0,Store.rows(c).length());assertEquals(0,FinancialEvents.rows(c).length());
 }
 @Test public void smsAppNotificationThenSmsIsOneMovement()throws Exception{
  long t=1_800_000_000_000L;
  assertTrue(Store.capture(c,UCO_ALERT,MESSAGES,t,"JD-UCOBNK-S",0).startsWith("Transaction added"));
  assertEquals("Duplicate ignored",Store.capture(c,UCO_ALERT,"sms",t+2000,"JD-UCOBNK-S",t+1000));
  assertEquals(1,Store.rows(c).length());JSONObject row=Store.rows(c).getJSONObject(0);assertEquals("SMS becomes the record","sms",row.getString("source"));assertFalse(row.getString("canonical").isEmpty());assertEquals("UCO",row.getString("issuer"));
  var later=new Store.Candidate(TransactionParser.parse(UCO_ALERT,"JD-UCOBNK-S"),Store.fingerprint(UCO_ALERT,t+5000),t+5000,UCO_ALERT,t+1000,"JD-UCOBNK-S");
  assertEquals(0,Store.importBatch(c,List.of(later),0).added());assertEquals(1,Store.rows(c).length());
 }
 @Test public void importedSmsThenActiveNotificationIsOneMovement()throws Exception{
  long t=1_800_000_000_000L;
  var sms=new Store.Candidate(TransactionParser.parse(UCO_ALERT,"JD-UCOBNK-S"),Store.fingerprint(UCO_ALERT,t),t,UCO_ALERT,t-500,"JD-UCOBNK-S");
  assertEquals(1,Store.importBatch(c,List.of(sms),0).added());
  assertEquals("Duplicate ignored",Store.capture(c,UCO_ALERT,MESSAGES,t+1500,"JD-UCOBNK-S",0));assertEquals(1,Store.rows(c).length());assertEquals("sms",Store.rows(c).getJSONObject(0).getString("source"));
  // A different alert (new balance) is a different payment, even for the same amount minutes later.
  String next=UCO_ALERT.replace("Rs.900.00","Rs.650.00");assertTrue(Store.capture(c,next,MESSAGES,t+120000,"JD-UCOBNK-S",0).startsWith("Transaction added"));
  // The same body far apart in time is not merged.
  assertTrue(Store.capture(c,UCO_ALERT,MESSAGES,t+COPY_GAP,"JD-UCOBNK-S",0).startsWith("Transaction added"));assertEquals(3,Store.rows(c).length());
 }
 static final long COPY_GAP=Store.COPY_WINDOW+60_000;

 @Test public void snapshotContractAndHttpsPolicy()throws Exception{
  Store.prefs(c).edit().putBoolean("pilot_consent",true).commit();Learning.observe(c,"IDFC A/c XX1234 debited INR 100 to Alice ref SECRET123456","AX-IDFCFB-S",TransactionParser.parse("Paid INR 100 to Alice"),"fixture-observation");JSONObject payload=Learning.snapshot(c);java.nio.file.Path directory=java.nio.file.Path.of(System.getProperty("pilot.testArtifacts"));java.nio.file.Files.createDirectories(directory);java.nio.file.Files.write(directory.resolve("android-snapshot.json"),payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));assertFalse(payload.toString().contains("Alice"));assertFalse(payload.toString().contains("SECRET"));
  assertEquals("https://example.com",PilotSync.base("https://example.com/"));for(String bad:List.of("http://example.com","https://user:pass@example.com","https://example.com/path","https://example.com?token=secret")){try{PilotSync.base(bad);fail("Accepted unsafe endpoint");}catch(IllegalArgumentException expected){}}
 }

 @Test public void correctionsQueueMaskedCasesOnce()throws Exception{
  Store.prefs(c).edit().putBoolean("pilot_consent",true).commit();Store.capture(c,"IDFC A/c XX1234 debited INR 100 to SecretCafe ref CASE123456","sms",1000,"AD-IDFCFB-S",900);JSONObject row=Store.rows(c).getJSONObject(0);Store.review(c,row.getString("id"),"Personal nickname","Groceries","debit",false);Store.review(c,row.getString("id"),"Another nickname","Health","debit",false);try(var cur=LedgerDb.get(c).getReadableDatabase().rawQuery("SELECT data FROM reports",null)){assertTrue(cur.moveToFirst());String report=cur.getString(0);assertTrue(report.contains("user_correction"));assertFalse(report.contains("SecretCafe"));assertFalse(report.contains("nickname"));assertFalse(cur.moveToNext());}
 }

}
