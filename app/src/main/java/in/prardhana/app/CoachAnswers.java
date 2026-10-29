package in.prardhana.app;

import org.json.*;
import java.time.*;
import java.util.*;

/** Offline explanations are deliberately bounded; no pretend open-ended model. */
final class CoachAnswers {
    static String answer(String question,PaynotesData.Overview month,CoachStore.Snapshot s){
        String q=question.toLowerCase(Locale.ROOT);JSONObject p=s.state().optJSONObject("plan"),g=s.state().optJSONObject("goal");
        if(q.contains("goal")||q.contains("sav")||q.contains("trip")||q.contains("laptop")){
            if(g==null)return "Let's make this concrete. Open Goal to enter what you're saving for, the amount and date. I'll calculate a weekly contribution and compare it with your monthly goal allocation. "+(p==null?"Start with My plan to add income and protected costs; income alone cannot establish affordability.":"Your current goal allocation is "+Store.money(p.optLong("goalMonthly"))+" per month.")+" Nothing changes until you review and confirm.";
            if(!LocalDate.parse(g.optString("date")).isAfter(LocalDate.now())&&MoneyPlan.funded(s.state())<g.optLong("amount"))return "Your goal date has arrived. You have confirmed "+Store.money(MoneyPlan.funded(s.state()))+" toward "+Store.money(g.optLong("amount"))+". Open Goal to choose a new date or revise the target. A missed date is a reason to replan, not a failure.";
            long weekly=MoneyPlan.weekly(s.state(),LocalDate.now()),monthly=(weekly*52+11)/12;
            return "For "+g.optString("name")+", you have confirmed "+Store.money(MoneyPlan.funded(s.state()))+" toward "+Store.money(g.optLong("amount"))+". Reaching the target by "+g.optString("date")+" requires about "+Store.money(weekly)+" a week ("+Store.money(monthly)+" a month). "+(p==null?"Add income and commitments in My plan to check the allocation.":monthly>p.optLong("goalMonthly")?"This exceeds your planned goal contribution. Consider a later date or a smaller target; review costs before raising your contribution.":"This is within your entered goal allocation, but it is not a guarantee of available cash.")+" Record only contributions you actually make. Under-budget spending is not automatically saved.";
        }
        if(q.contains("plan")||q.contains("limit")||q.contains("afford")||q.contains("income")||q.contains("budget")||q.contains("target")){
            if(p==null)return "Open My plan to enter take-home pay, payday, protected costs and a goal contribution. I'll show what remains for flexible spending. Expected pay and captured messages are not a verified bank balance. You can revise every assumption before confirming.";
            long allocation=MoneyPlan.flexible(p),left=CoachStore.remaining(s);
            return "Your plan allocates "+Store.money(allocation)+" to flexible spending per pay cycle. You have recorded "+Store.money(s.spent())+" since "+s.start()+", leaving "+Store.money(left)+" in the plan until "+s.next()+". "+(left<0?"The plan needs adjustment. Review flexible costs, reduce a goal contribution or revise the timeline rather than cutting essentials automatically. ":"")+"This is not your available bank balance. "+s.unknown()+" payments lack confirmed allocation; bills covered by protected costs should be marked committed in Activity. Open My plan or Limits to review a change.";
        }
        if(q.contains("stress")||q.contains("guilt")||q.contains("habit")||q.contains("control"))return "Start with one change you choose, not a complete spending overhaul. Protect something you value, pick one flexible category to experiment with this week, and review whether it helped. An expensive week is a reason to replan, not a failure. What spending would you most like to keep? You can remember that preference in My plan.";
        if(q.contains("repay")||q.contains("friend")||q.contains("transfer"))return "You have "+Store.money(s.awaiting())+" awaiting repayment on confirmed shared payments. It is not available cash. In Activity, open a payment and choose Purpose to set your share or link an incoming repayment. A payment to a person may still be spending; recipient names alone are not enough.";
        if(q.contains("where")||q.contains("categor")){
            StringBuilder out=new StringBuilder("Recorded personal spending this month: "+Store.money(month.spent)+".\n");int n=0;JSONObject limits=s.state().optJSONObject("limits");
            for(var entry:month.categories()){if(n++>=5)break;out.append("\n").append(entry.getKey()).append(": ").append(Store.money(entry.getValue()));if(limits!=null&&limits.has(entry.getKey()))out.append(" / limit ").append(Store.money(limits.optLong(entry.getKey())));}
            return out+"\n\nReview the underlying payments in Overview. Shared-payment corrections and linked refunds adjust personal spending; capture gaps can still change the picture.";
        }
        if(q.contains("month")||q.contains("compar")||q.contains("spend"))return "Recorded personal spending this month is "+Store.money(month.spent)+". "+(month.priorEntries>0?"At the same point last month it was "+Store.money(month.previousSpent)+".":"There is no comparable recorded history yet.")+" Open Overview to inspect the change, or ask about your plan, goal or repayments. These records may be incomplete.";
        return "Offline coaching can explain your spending, payday plan, goal progress and repayments. Use My plan, Goal or Limits to make a reviewed change. For an open-ended conversation, configure the optional AI connection in Settings. No data was sent.";
    }
}
