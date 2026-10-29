package in.prardhana.app;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.json.*;

/** Pure integer-paise planning. A spending plan is not a bank balance. */
final class MoneyPlan {
    static final long MAX = 100_000_000_000L;
    static long amount(String input) {
        try {
            long n = new BigDecimal(input.trim()).movePointRight(2).longValueExact();
            if(n<0 || n>MAX) throw new IllegalArgumentException();
            return n;
        } catch(Exception e) { throw new IllegalArgumentException("Enter a non-negative rupee amount with up to two decimals."); }
    }
    static String rupees(long amount) { return BigDecimal.valueOf(amount,2).toPlainString(); }
    static LocalDate payday(YearMonth month,int day) { return month.atDay(Math.min(day,month.lengthOfMonth())); }
    static LocalDate start(LocalDate today,int day) {
        LocalDate current=payday(YearMonth.from(today),day);
        return current.isAfter(today)?payday(YearMonth.from(today).minusMonths(1),day):current;
    }
    static LocalDate next(LocalDate today,int day) { return payday(YearMonth.from(start(today,day)).plusMonths(1),day); }
    static long flexible(JSONObject plan) {
        return plan.optLong("income")-plan.optLong("fixed")-plan.optLong("essentials")-plan.optLong("debt")-plan.optLong("reserve")-plan.optLong("goalMonthly");
    }
    static long funded(JSONObject state) {
        long result=0; JSONArray entries=state.optJSONArray("contributions");
        if(entries!=null)for(int i=0;i<entries.length();i++) result+=entries.optJSONObject(i).optLong("amount");
        return result;
    }
    static long weekly(JSONObject state,LocalDate today) {
        JSONObject goal=state.optJSONObject("goal"); if(goal==null)return 0;
        long left=Math.max(0,goal.optLong("amount")-funded(state));
        long days=ChronoUnit.DAYS.between(today,LocalDate.parse(goal.optString("date")));
        long weeks=Math.max(1,(days+6)/7);
        return (left+weeks-1)/weeks;
    }
    static JSONObject empty() { return new JSONObject(); }
}
