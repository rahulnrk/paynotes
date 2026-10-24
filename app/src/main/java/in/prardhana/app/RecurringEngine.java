package in.prardhana.app;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Transparent periodicity candidates; a merchant label alone never establishes a product. */
public final class RecurringEngine {
    public record Payment(String id,String identity,String merchant,String category,long amount,LocalDate day,boolean reviewed){}
    public record Series(String identity,String merchant,String kind,String cadence,int intervalDays,LocalDate next,long latest,long low,long high,int observations,boolean variable,boolean reviewed,String evidence){}
    public static List<Series> analyze(List<Payment> input,LocalDate today){
        Map<String,List<Payment>> groups=new TreeMap<>();
        for(Payment p:input)if(p.amount>0&&!p.day.isAfter(today)&&!p.merchant.isBlank()&&!p.merchant.equals("Unknown merchant")&&!List.of("Transfer","Cash withdrawal","Payment receipt","Not a transaction").contains(p.category))groups.computeIfAbsent(p.identity,k->new ArrayList<>()).add(p);
        List<Series> out=new ArrayList<>();
        for(var e:groups.entrySet()){
            List<Payment> all=e.getValue();all.sort(Comparator.comparing(Payment::day));if(all.size()<2)continue;
            List<Payment> h=all.subList(Math.max(0,all.size()-6),all.size());List<Long> gaps=new ArrayList<>();for(int i=1;i<h.size();i++)gaps.add(ChronoUnit.DAYS.between(h.get(i-1).day,h.get(i).day));
            if(gaps.stream().anyMatch(g->g<=0))continue;List<Long> sorted=new ArrayList<>(gaps);Collections.sort(sorted);int interval=sorted.get(sorted.size()/2).intValue();
            String cadence=interval>=6&&interval<=8?"Weekly":interval>=13&&interval<=15?"Fortnightly":interval>=27&&interval<=29&&h.get(h.size()-1).category.equals("Recharge")?"Every 28 days":interval>=26&&interval<=35?"Monthly":interval>=80&&interval<=100?"Quarterly":interval>=350&&interval<=380?"Annual":"";
            if(cadence.isEmpty())continue;int tolerance=cadence.equals("Annual")?15:cadence.equals("Quarterly")?10:cadence.equals("Monthly")?5:2;
            if(gaps.stream().anyMatch(g->Math.abs(g-interval)>tolerance))continue;
            Payment last=h.get(h.size()-1);LocalDate next=switch(cadence){case "Monthly"->last.day.plusMonths(1);case "Quarterly"->last.day.plusMonths(3);case "Annual"->last.day.plusYears(1);default->last.day.plusDays(interval);};
            if(cadence.equals("Monthly")&&h.stream().allMatch(p->p.day.getDayOfMonth()==p.day.lengthOfMonth()))next=next.withDayOfMonth(next.lengthOfMonth());
            if(next.isBefore(today.minusDays(14)))continue;
            long low=h.stream().mapToLong(Payment::amount).min().orElse(0),high=h.stream().mapToLong(Payment::amount).max().orElse(0);boolean variable=high-low>Math.max(1,low/20);
            String kind=switch(last.category){case "Subscriptions","Entertainment"->"Subscription";case "Debt repayment"->"EMI / loan";case "Bills","Utilities"->"Utility";case "Housing"->"Rent / society";case "Recharge"->"Recharge";case "Insurance"->"Insurance";default->"Recurring payment";};
            boolean reviewed=h.stream().allMatch(Payment::reviewed);String evidence=h.size()+" observed payments; "+(reviewed?"all reviewed":"some need review")+". Same payee can bill multiple products; confirm the series.";
            out.add(new Series(e.getKey(),last.merchant,kind,cadence,interval,next,last.amount,low,high,h.size(),variable,reviewed,evidence));
        }
        out.sort(Comparator.comparing(Series::next));return out;
    }
}
