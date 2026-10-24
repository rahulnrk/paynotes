import in.prardhana.app.*;
import java.time.*;
import java.util.*;

public class InsightsTest {
    static final LocalDate NOW=LocalDate.of(2026,10,23);
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static InsightEngine.Tx tx(String id,long amount,String category,LocalDate day,boolean reviewed){return new InsightEngine.Tx(id,amount,"debit","Netflix",category,day,reviewed);}
    static List<InsightEngine.Insight> analyze(List<InsightEngine.Tx> rows){return InsightEngine.analyze(rows,NOW,0,0,"Monthly");}
    static boolean has(List<InsightEngine.Insight> a,String type){return a.stream().anyMatch(i->i.type().equals(type));}
    public static void main(String[] args){
        List<InsightEngine.Tx> recurring=List.of(tx("a",19900,"Entertainment",NOW.minusMonths(3).plusDays(5),true),tx("b",19900,"Entertainment",NOW.minusMonths(2).plusDays(5),true),tx("c",24900,"Entertainment",NOW.minusMonths(1).plusDays(5),true));
        var findings=analyze(recurring);check(has(findings,"recurring")&&has(findings,"price")&&has(findings,"bill"),"monthly pattern, price increase and next-date forecast");
        check(has(analyze(recurring.stream().map(t->tx(t.id(),t.paise(),"Subscriptions",t.day(),true)).toList()),"recurring"),"Subscriptions category participates in recurrence");
        check(findings.stream().filter(i->i.type().equals("bill")).findFirst().get().facts().get("daysUntil")==5L,"forecast date");
        check(analyze(recurring.stream().map(t->tx(t.id(),t.paise(),t.category(),t.day(),false)).toList()).isEmpty(),"unreviewed rows excluded");
        check(analyze(recurring.stream().map(t->tx(t.id(),t.paise(),"Transfer",t.day(),true)).toList()).isEmpty(),"transfers excluded");
        check(analyze(recurring.stream().map(t->tx(t.id(),t.paise(),t.category(),t.day().minusYears(1),true)).toList()).isEmpty(),"stale subscription not active");
        check(analyze(List.of(recurring.get(0),recurring.get(1))).isEmpty(),"two payments insufficient");
        check(analyze(List.of(recurring.get(0),recurring.get(1),tx("c",24900,"Entertainment",NOW.minusDays(3),true))).isEmpty(),"irregular interval not recurring");
        List<InsightEngine.Tx> purchases=new ArrayList<>();for(int i=0;i<8;i++)purchases.add(tx("p"+i,10000,"Shopping",NOW.minusDays(10+i),true));purchases.add(tx("large",60000,"Shopping",NOW,true));
        check(has(analyze(purchases),"anomaly"),"large purchase vs enough historical evidence");
        purchases.remove(0);check(!has(analyze(purchases),"anomaly"),"insufficient anomaly baseline");
        purchases.add(tx("future",9999999,"Shopping",NOW.plusDays(1),true));check(!has(analyze(purchases),"anomaly"),"future data excluded");
        check(!has(InsightEngine.analyze(List.of(),NOW,10000,7999,"Daily"),"budget"),"below threshold");
        check(has(InsightEngine.analyze(List.of(),NOW,10000,8000,"Daily"),"budget"),"80 percent threshold");
        check(!has(InsightEngine.analyze(List.of(),NOW,0,10000,"Daily"),"budget"),"no limit no budget warning");
        var parsed=TransactionParser.parse("Paid INR 240 to Swiggy ref ABC123456");
        check(DuplicatePolicy.same(parsed,"sms",1000,24000,"debit","Swiggy","ABC123456","bank.app",500),"SMS/notification shared reference");
        check(!DuplicatePolicy.same(parsed,"sms",1000,24000,"debit","Swiggy","OTHER12345","sms",999),"distinct references survive close together");
        check(!DuplicatePolicy.same(parsed,"sms",1000,24000,"credit","Swiggy","ABC123456","sms",999),"credit/debit not merged");
        check(!DuplicatePolicy.same(parsed,"sms",1000,24100,"debit","Swiggy","ABC123456","sms",999),"different amount not merged");
        System.out.println("Insight and duplicate checks passed");
    }
}
