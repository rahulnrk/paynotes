package in.prardhana.app;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import java.time.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class CoachTest {
    android.content.Context context; LedgerDb db;
    LocalDate today=LocalDate.now();
    @Before public void setup()throws Exception {context=RuntimeEnvironment.getApplication();reset();Store.prefs(context).edit().clear().commit();db=LedgerDb.get(context);}
    @After public void cleanup()throws Exception {reset();}
    void reset()throws Exception{var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);LedgerDb old=(LedgerDb)f.get(null);if(old!=null)old.close();f.set(null,null);context.deleteDatabase("ledger.db");}
    JSONObject payment(String id,long amount,String direction)throws Exception{
        JSONObject r=new JSONObject().put("id",id).put("time",today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()).put("paise",amount).put("direction",direction).put("category","Food & drink").put("source","manual");db.put(r);return r;
    }
    JSONObject plan()throws Exception{return new JSONObject().put("income",6000000).put("fixed",2500000).put("essentials",1200000).put("debt",500000).put("reserve",300000).put("goalMonthly",500000).put("payday",1).put("protect","Meals with friends");}
    @Test public void exactPaiseAndInvalidInputs(){assertEquals(12345,MoneyPlan.amount("123.45"));for(String s:new String[]{"-1","0.001","NaN","Infinity","99999999999999",""})assertThrows(IllegalArgumentException.class,()->MoneyPlan.amount(s));}
    @Test public void paydayClampsFebruaryWithoutDrifting(){assertEquals(LocalDate.of(2026,2,28),MoneyPlan.start(LocalDate.of(2026,3,1),31));assertEquals(LocalDate.of(2026,3,31),MoneyPlan.next(LocalDate.of(2026,3,1),31));assertEquals(LocalDate.of(2024,2,29),MoneyPlan.start(LocalDate.of(2024,2,29),31));}
    @Test public void allocationsAndOverrunsAreNotAvailableCash()throws Exception{
        CoachStore.save(db,new JSONObject().put("plan",plan()));payment("flex",200000,"debit");payment("bill",4600000,"debit");CoachStore.meaning(db,"bill",4600000,"committed","personal","");
        var s=CoachStore.snapshot(db,today);assertEquals(1000000,MoneyPlan.flexible(plan()));assertEquals(100000,CoachStore.committedOverrun(s));assertEquals(700000,CoachStore.remaining(s));assertEquals(700000,CoachStore.facts(s).getLong("remainingPlanPaise"));
    }
    @Test public void sharedPaymentAndPartialRepaymentsRemainSeparate()throws Exception{
        payment("dinner",240000,"debit");CoachStore.meaning(db,"dinner",60000,"flexible","shared","");payment("repay",100000,"credit");CoachStore.meaning(db,"repay",0,"flexible","reimbursement","dinner");
        var s=CoachStore.snapshot(db,today);assertEquals(60000,s.spent());assertEquals(80000,s.awaiting());
        var o=PaynotesData.overview(db,"Month",today,ZonedDateTime.now());assertEquals(60000,o.spent);assertEquals(100000,o.moneyIn);
        payment("tooMuch",90000,"credit");assertThrows(IllegalArgumentException.class,()->CoachStore.meaning(db,"tooMuch",0,"flexible","reimbursement","dinner"));
        assertThrows(IllegalArgumentException.class,()->CoachStore.meaning(db,"dinner",240000,"flexible","personal",""));
    }
    @Test public void refundsAdjustOriginalPersonalSpendingAndDeletionRestoresIt()throws Exception{
        payment("purchase",100000,"debit");payment("refund",25000,"credit");CoachStore.meaning(db,"refund",0,"flexible","refund","purchase");assertEquals(75000,CoachStore.snapshot(db,today).spent());
        db.remove("refund");assertEquals(100000,CoachStore.snapshot(db,today).spent());
    }
    @Test public void transfersAndSettlementsAreExcludedWithoutRewritingEvidence()throws Exception{
        payment("transfer",100000,"debit");CoachStore.meaning(db,"transfer",0,"flexible","transfer","");payment("card",200000,"debit");CoachStore.meaning(db,"card",0,"committed","settlement","");
        assertEquals(0,CoachStore.snapshot(db,today).spent());assertEquals(0,PaynotesData.overview(db,"Month",today,ZonedDateTime.now()).spent);assertEquals(100000,db.byId("transfer").getLong("paise"));
    }
    @Test public void fundingCountsWithdrawalsAndDoesNotCreateMoney()throws Exception{
        JSONObject state=new JSONObject().put("goal",new JSONObject().put("name","Trip").put("amount",600000).put("date",today.plusWeeks(12).toString())).put("contributions",new JSONArray().put(new JSONObject().put("amount",100000)).put(new JSONObject().put("amount",-40000)));
        assertEquals(60000,MoneyPlan.funded(state));assertEquals(45000,MoneyPlan.weekly(state,today));CoachStore.save(db,state);assertEquals(60000,MoneyPlan.funded(CoachStore.load(db)));db.clear();assertFalse(CoachStore.load(db).has("goal"));
    }
    @Test public void unknownIncomeNeverBecomesSalary()throws Exception{
        payment("incoming",9000000,"credit");var o=PaynotesData.overview(db,"Month",today,ZonedDateTime.now());String answer=CoachAnswers.answer("Can I afford a laptop?",o,CoachStore.snapshot(db,today));assertTrue(answer.contains("My plan"));assertFalse(CoachStore.facts(CoachStore.snapshot(db,today)).getBoolean("planSet"));
    }
    @Test public void linkedCreditsRequireValidOriginal()throws Exception{payment("credit",100,"credit");assertThrows(IllegalArgumentException.class,()->CoachStore.meaning(db,"credit",0,"flexible","refund","missing"));}
    @Test public void suggestionsCannotExecuteOrAcceptFractionalPaise()throws Exception{
        JSONObject proposal=new JSONObject().put("type","goal").put("name","Laptop").put("amountPaise",6000000).put("date",today.plusMonths(6).toString());
        CoachProposal.validate(proposal);assertEquals(0,CoachStore.load(db).length());
        proposal.put("amountPaise",0.5);assertThrows(IllegalArgumentException.class,()->CoachProposal.validate(proposal));
        proposal.put("amountPaise",50000).put("type","transfer");assertThrows(IllegalArgumentException.class,()->CoachProposal.validate(proposal));
    }
    @Test public void editedAmountCannotInvalidateConfirmedShares()throws Exception{
        JSONObject row=payment("shared",100000,"debit");CoachStore.meaning(db,"shared",50000,"flexible","shared","");row.put("paise",40000);
        assertThrows(IllegalArgumentException.class,()->db.put(row));assertEquals(100000,db.byId("shared").getLong("paise"));
    }
    @Test public void spendingFiltersAndLegacyTotalsRespectConfirmedTransfers()throws Exception{
        payment("transfer",100000,"debit");CoachStore.meaning(db,"transfer",0,"flexible","transfer","");payment("personal",50000,"debit");
        assertEquals(50000,db.spent(0,System.currentTimeMillis()));
        assertEquals(1,PaynotesData.payments(db,new PaynotesData.Filter("Spending","",null,0,0,""),100).count());
        assertEquals(1,PaynotesData.payments(db,new PaynotesData.Filter("Transfers","",null,0,0,""),100).count());
    }
    @Test public void migrationPreservesExistingMovements()throws Exception{
        payment("old",500,"debit");db.getWritableDatabase().execSQL("DROP TABLE coach_state");db.getWritableDatabase().execSQL("DROP TABLE payment_meaning");db.getWritableDatabase().setVersion(2);db.close();var f=LedgerDb.class.getDeclaredField("instance");f.setAccessible(true);f.set(null,null);db=LedgerDb.get(context);assertNotNull(db.byId("old"));assertEquals(500,CoachStore.snapshot(db,today).spent());
    }
}
