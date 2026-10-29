package in.prardhana.app;
import org.json.*;
import java.time.*;
import java.util.*;

/** Untrusted model suggestions may prefill a form, never execute a financial action. */
final class CoachProposal {
    static void validate(JSONObject p)throws JSONException {
        if(p.length()!=4||!p.has("date")||!p.has("type")||!p.has("name")||!p.has("amountPaise"))throw new IllegalArgumentException("Invalid proposal");
        String type=p.getString("type"),name=p.getString("name");Object raw=p.get("amountPaise");
        if(!(raw instanceof Number)||raw instanceof Double||raw instanceof Float||p.getLong("amountPaise")<=0||p.getLong("amountPaise")>MoneyPlan.MAX||name.isBlank()||name.length()>100)throw new IllegalArgumentException("Invalid proposal value");
        if(type.equals("goal")){LocalDate date=LocalDate.parse(p.getString("date"));if(!date.isAfter(LocalDate.now())||date.isAfter(LocalDate.now().plusYears(50)))throw new IllegalArgumentException("Choose a future goal date");}
        else if(!type.equals("limit")||!p.isNull("date")||!List.of("Food & drink","Groceries","Shopping","Transport","Entertainment","Bills","Other").contains(name))throw new IllegalArgumentException("Invalid category limit");
    }
}
