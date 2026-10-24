package in.prardhana.app;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Deterministic statistical evidence, kept separate from optional AI-generated wording. */
public final class InsightEngine {
    public record Tx(String id, long paise, String direction, String merchant, String category, LocalDate day, boolean reviewed) {}
    public record Insight(String id, String type, String title, String detail, String confidence, Map<String,Long> facts) {}
    private static String money(long p) { return java.text.NumberFormat.getCurrencyInstance(new Locale("en","IN")).format(p/100.0); }
    private static long median(List<Long> values) { List<Long> a=new ArrayList<>(values); Collections.sort(a);return a.get(a.size()/2); }
    public static List<Insight> analyze(List<Tx> input, LocalDate today, long budget, long spent, String period) {
        List<Insight> out=new ArrayList<>();
        if(budget>0 && spent>=budget*0.8) {
            boolean over=spent>budget;
            out.add(new Insight("budget","budget",over?"Your budget needs attention":"Approaching your limit",
                money(spent)+" recorded against your "+period.toLowerCase(Locale.ROOT)+" limit of "+money(budget)+". "+(over?money(spent-budget)+" above the limit.":money(budget-spent)+" remains."),"Recorded totals",
                Map.of("spentPaise",spent,"budgetPaise",budget)));
        }
        List<Tx> reviewed=input.stream().filter(t->t.reviewed && t.direction.equals("debit") && !t.category.equals("Transfer") && !t.category.equals("Not a transaction") && !t.category.equals("Payment receipt") && !t.day.isAfter(today)).sorted(Comparator.comparing(Tx::day)).toList();
        Map<String,List<Tx>> merchants=new TreeMap<>();
        for(Tx t:reviewed) if(!t.merchant.equals("Unknown merchant") && List.of("Bills","Entertainment","Other","Subscriptions").contains(t.category))
            merchants.computeIfAbsent(t.merchant.trim().toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(t);
        int count=0;
        for(List<Tx> history:merchants.values()) {
            if(history.size()<3)continue;
            List<Tx> h=history.subList(history.size()-3,history.size()); Tx a=h.get(0), b=h.get(1), last=h.get(2);
            long firstGap=ChronoUnit.DAYS.between(a.day,b.day), secondGap=ChronoUnit.DAYS.between(b.day,last.day);
            if(firstGap<26||firstGap>35||secondGap<26||secondGap>35)continue;
            if(Math.abs(a.paise-b.paise)>a.paise*0.05)continue;
            boolean hike=last.paise>=b.paise*1.10;
            if(!hike && Math.abs(last.paise-b.paise)>b.paise*0.05)continue;
            LocalDate due=last.day.plusMonths(1);long days=ChronoUnit.DAYS.between(today,due);
            // An old series does not establish an active commitment.
            if(days< -7 || last.day.isBefore(today.minusDays(40)))continue;
            String id="recurring-"+(count++);
            out.add(new Insight(id,"recurring","Possible recurring payment · "+last.merchant,
                "Three reviewed payments, "+firstGap+" and "+secondGap+" days apart. Latest: "+money(last.paise)+". This may be a subscription or bill; confirm with the provider.","Possible",Map.of("payments",3L,"intervalDays",(firstGap+secondGap)/2,"latestPaise",last.paise)));
            if(hike)out.add(new Insight(id+"-price","price","Possible price increase · "+last.merchant,
                "Latest reviewed payment was "+money(last.paise)+", previously "+money(b.paise)+" ("+Math.round(100.0*(last.paise-b.paise)/b.paise)+"% higher). A changed plan or one-off fee can also explain this.","Possible",Map.of("previousPaise",b.paise,"latestPaise",last.paise)));
            if(days>=0&&days<=7)out.add(new Insight(id+"-due","bill","A payment may be coming up",
                last.merchant+" may charge around "+due+", based on its monthly pattern. Estimated amount: "+money(last.paise)+". This is a forecast, not a confirmed due date.","Estimate",Map.of("daysUntil",days,"estimatedPaise",last.paise)));
        }
        for(Tx candidate:reviewed) {
            if(candidate.day.isBefore(today.minusDays(6)))continue;
            List<Long> baseline=reviewed.stream().filter(t->t.category.equals(candidate.category)&&t.day.isBefore(candidate.day)&&!t.day.isBefore(candidate.day.minusDays(90))).map(Tx::paise).toList();
            if(baseline.size()<8)continue;
            long median=median(baseline);
            if(candidate.paise>=50000 && candidate.paise>=3.0*median)out.add(new Insight("unusual-"+candidate.id,"anomaly","Larger than usual · "+candidate.category,
                money(candidate.paise)+" at "+candidate.merchant+" is at least three times the median of "+baseline.size()+" earlier reviewed purchases in this category ("+money(median)+"). Check whether it was expected; this does not establish fraud.","Statistical flag",Map.of("amountPaise",candidate.paise,"medianPaise",median,"baselineCount",(long)baseline.size())));
        }
        return out.subList(0,Math.min(12,out.size()));
    }
}
