package in.prardhana.app;

import android.content.Context;
import org.json.*;
import java.time.*;
import java.util.*;

final class Insights {
    static List<InsightEngine.Insight> local(Context c) {
        List<InsightEngine.Tx> input=new ArrayList<>(); JSONArray rows=LedgerDb.get(c).recent(System.currentTimeMillis()-800L*86400000L,true);
        String period=Store.prefs(c).getString("period","Monthly");long spent=0, start=Store.start(period), now=System.currentTimeMillis();
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i);
            if(r.optString("source").equals("sample")||r.optString("category").equals("Not a transaction")||r.optString("category").equals("Payment receipt"))continue;
            if(r.optLong("time")>=start&&r.optLong("time")<=now&&r.optString("direction").equals("debit")&&!r.optString("category").equals("Transfer"))spent+=r.optLong("paise");
            input.add(new InsightEngine.Tx(r.optString("id"),r.optLong("paise"),r.optString("direction"),r.optString("merchant"),r.optString("category"),Instant.ofEpochMilli(r.optLong("time")).atZone(ZoneId.systemDefault()).toLocalDate(),r.optBoolean("reviewed")));
        }
        return InsightEngine.analyze(input,LocalDate.now(),Store.prefs(c).getLong("budget",0),Store.spent(c,period),period);
    }
    // Deliberately excludes free text, dates, identifiers, merchants, and message sources.
    static JSONObject cloudPayload(List<InsightEngine.Insight> insights) throws JSONException {
        JSONArray facts=new JSONArray();int i=0;
        for(InsightEngine.Insight insight:insights) facts.put(new JSONObject().put("id","finding-"+(i++)).put("type",insight.type()).put("facts",new JSONObject(insight.facts())));
        return new JSONObject().put("findings",facts);
    }
}
