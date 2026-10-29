package in.prardhana.app;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class PaynotesTest {
 android.content.Context c;
 final ZoneId zone=ZoneId.of("Asia/Kolkata");
 ZonedDateTime now=ZonedDateTime.of(2026,10,25,12,0,0,0,zone);
 @Before public void setup()throws Exception{c=RuntimeEnvironment.getApplication();reset();Store.prefs(c).edit().clear().commit();}
 @After public void cleanup()throws Exception{reset();}
 void reset()throws Exception{var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);LedgerDb db=(LedgerDb)f.get(null);if(db!=null)db.close();f.set(null,null);c.deleteDatabase("ledger.db");}
 long at(String date){return LocalDateTime.parse(date).atZone(zone).toInstant().toEpochMilli();}
 void add(String id,String date,long amount,String dir,String category,String issuer,String account,String source)throws Exception{LedgerDb.get(c).put(new JSONObject().put("id",id).put("time",at(date)).put("paise",amount).put("direction",dir).put("category",category).put("merchant",id).put("issuer",issuer).put("account",account).put("source",source));}
 @Test public void periodsCompareEqualLocalElapsedTime(){
  SpendingPeriod day=new SpendingPeriod("Day",now.toLocalDate(),now);
  assertEquals(at("2026-10-24T12:00")+1,day.previousUntil);
  SpendingPeriod week=new SpendingPeriod("Week",now.toLocalDate(),now);
  assertEquals(LocalDate.of(2026,10,19),week.start);assertEquals(at("2026-10-18T12:00")+1,week.previousUntil);
  SpendingPeriod month=new SpendingPeriod("Month",now.toLocalDate(),now);
  assertEquals(at("2026-09-25T12:00")+1,month.previousUntil);
  SpendingPeriod past=new SpendingPeriod("Month",LocalDate.of(2026,1,15),now);
  assertEquals(LocalDate.of(2025,12,1),past.previous);assertEquals(at("2026-01-01T00:00"),past.previousUntil);
 }
 @Test public void shorterMonthsAndDstDoNotLosePriorData(){
  ZonedDateTime march=ZonedDateTime.of(2026,3,31,12,0,0,0,zone);
  SpendingPeriod month=new SpendingPeriod("Month",march.toLocalDate(),march);assertEquals(at("2026-03-01T00:00"),month.previousUntil);
  ZonedDateTime dst=ZonedDateTime.of(2026,3,8,12,0,0,0,ZoneId.of("America/New_York"));
  SpendingPeriod day=new SpendingPeriod("Day",dst.toLocalDate(),dst);
  assertEquals(dst.minusDays(1).toInstant().toEpochMilli()+1,day.previousUntil);
 }
 @Test public void overviewAndChartExcludeNonSpendingAndKeepUnequalMonthLengths()throws Exception{
  add("coffee","2026-10-25T10:00",25000,"debit","Food & drink","Bank","XX1","sms");
  add("cash","2026-10-25T10:10",100000,"debit","Cash withdrawal","Bank","XX1","sms");
  add("transfer","2026-10-25T10:20",200000,"debit","Transfer","Bank","XX1","sms");
  add("receipt","2026-10-25T10:30",25000,"debit","Payment receipt","Bank","XX1","sms");
  add("sample","2026-10-25T10:40",990000,"debit","Other","Bank","XX1","sample");
  add("excluded","2026-10-25T10:50",990000,"debit","Not a transaction","Bank","XX1","sms");
  add("income","2026-10-25T11:00",500000,"credit","Other","Bank","XX1","sms");
  add("prior","2026-09-25T11:00",30000,"debit","Other","Bank","XX1","sms");
  add("later","2026-09-25T13:00",990000,"debit","Other","Bank","XX1","sms");
  var o=PaynotesData.overview(LedgerDb.get(c),"Month",now.toLocalDate(),now);
  assertEquals(25000,o.spent);assertEquals(30000,o.previousSpent);assertEquals(100000,o.cash);assertEquals(200000,o.transfers);assertEquals(500000,o.moneyIn);assertEquals(o.spent,Arrays.stream(o.bars).sum());assertEquals(o.previousSpent,Arrays.stream(o.prior).sum());
  add("jan31","2026-01-31T21:00",1100,"debit","Other","Bank","XX1","sms");
  var feb=PaynotesData.overview(LedgerDb.get(c),"Month",LocalDate.of(2026,2,10),now);assertEquals(28,feb.bars.length);assertEquals(31,feb.prior.length);assertEquals(1100,Arrays.stream(feb.prior).sum());
  String facts=PaynotesData.chatFacts(o,50000).toString();assertFalse(facts.contains("coffee"));assertFalse(facts.contains("XX1"));assertFalse(facts.contains("Bank"));
 }
 @Test public void filtersCombineExactAccountsTypesDatesAndLiteralSearch()throws Exception{
  add("coffee%one","2026-10-25T10:00",25000,"debit","Food & drink","Bank","XX1","sms");
  add("coffeeXtwo","2026-10-25T11:00",30000,"debit","Food & drink","Bank","XX12","sms");
  add("coffee%three","2026-10-24T10:00",30000,"credit","Other","Other bank","XX1","sms");
  var filter=new PaynotesData.Filter("Spending","%",new PaynotesData.Account("Bank","XX1"),at("2026-10-25T00:00"),at("2026-10-26T00:00"),"Food & drink");
  var payments=PaynotesData.payments(LedgerDb.get(c),filter,100);assertEquals(1,payments.count());assertEquals("coffee%one",payments.rows().get(0).getString("id"));
  assertEquals(3,PaynotesData.accounts(LedgerDb.get(c)).size());
  var all=PaynotesData.payments(LedgerDb.get(c),new PaynotesData.Filter("All payments","",null,0,0,""),1);assertEquals(3,all.count());assertEquals(1,all.rows().size());assertEquals("coffeeXtwo",all.rows().get(0).getString("id"));
 }
}
