package in.prardhana.app;

import android.database.Cursor;
import org.json.*;
import java.time.*;
import java.util.*;

/** Read models for Paynotes. Call on a worker, never while building a view. */
final class PaynotesData {
    record Account(String issuer, String mask) {
        String label() { return (issuer.isEmpty() ? "Unknown bank" : issuer) + (mask.isEmpty() ? " · account not supplied" : " · " + mask); }
    }
    static final class Overview {
        final SpendingPeriod period;
        final long[] bars, prior;
        long spent, previousSpent, moneyIn, cash, transfers, lastCaptured;
        int entries, priorEntries, unreviewed, totalEntries;
        final Map<String,Long> categories = new HashMap<>();
        Overview(SpendingPeriod period) { this.period = period; bars = new long[period.buckets]; prior = new long[period.mode.equals("Month")?period.previous.lengthOfMonth():period.buckets]; }
        List<Map.Entry<String,Long>> categories() { List<Map.Entry<String,Long>> list = new ArrayList<>(categories.entrySet()); list.sort(Map.Entry.<String,Long>comparingByValue().reversed()); return list; }
    }
    static boolean spending(String direction, String category) {
        return direction.equals("debit") && !List.of("Transfer", "Cash withdrawal", "Not a transaction", "Payment receipt").contains(category);
    }
    static Overview overview(LedgerDb db, String mode, LocalDate date, ZonedDateTime now) {
        Overview o = new Overview(new SpendingPeriod(mode, date, now));
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*),MAX(time) FROM movements WHERE source!='sample' AND category NOT IN ('Not a transaction','Payment receipt')", null)) {
            c.moveToFirst(); o.totalEntries = c.getInt(0); o.lastCaptured = c.getLong(1);
        }
        readPeriod(db, o, false);
        readPeriod(db, o, true);
        return o;
    }
    private static void readPeriod(LedgerDb db, Overview o, boolean prior) {
        SpendingPeriod p = o.period;
        long from = prior ? p.millis(p.previous) : p.from, to = prior ? p.previousUntil : p.until;
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT m.time,m.amount,m.direction,m.category,m.reviewed,p.share,p.kind,COALESCE((SELECT SUM(r.amount) FROM payment_meaning x JOIN movements r ON r.id=x.id WHERE x.linked=m.id AND x.kind='refund' AND r.direction='credit' AND r.category!='Not a transaction' AND r.source!='sample'),0) FROM movements m LEFT JOIN payment_meaning p ON p.id=m.id WHERE m.time>=? AND m.time<? AND m.source!='sample' AND m.category NOT IN ('Not a transaction','Payment receipt')", new String[]{""+from,""+to})) {
            while (c.moveToNext()) {
                long amount = c.getLong(1); String direction = c.getString(2), category = c.getString(3);
                if (prior) o.priorEntries++; else {
                    o.entries++; if (c.getInt(4)==0) o.unreviewed++;
                    if (direction.equals("credit")) o.moneyIn += amount;
                    if (direction.equals("debit") && category.equals("Cash withdrawal")) o.cash += amount;
                    if (direction.equals("debit") && ("transfer".equals(c.getString(6)) || (category.equals("Transfer")&&c.isNull(5)))) o.transfers += amount;
                }
                if (!spending(direction, category) && !(direction.equals("debit") && !c.isNull(5))) continue;
                amount=Math.max(0,(c.isNull(5)?amount:c.getLong(5))-c.getLong(7));
                if(amount==0)continue;
                int bucket = p.bucket(c.getLong(0), prior ? p.previous : p.start);
                if (prior) { o.previousSpent += amount; if(bucket>=0 && bucket<o.prior.length) o.prior[bucket] += amount; }
                else { o.spent += amount; if(bucket>=0 && bucket<o.bars.length) o.bars[bucket] += amount; o.categories.merge(category,amount,Long::sum); }
            }
        }
    }
    static List<Account> accounts(LedgerDb db) {
        List<Account> result = new ArrayList<>();
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT DISTINCT issuer,account FROM movements WHERE source!='sample' ORDER BY issuer,account",null)) {
            while(c.moveToNext()) result.add(new Account(c.getString(0),c.getString(1)));
        }
        return result;
    }
    record Filter(String type, String query, Account account, long from, long until, String category) {}
    record Payments(List<JSONObject> rows, int count) {}
    static Payments payments(LedgerDb db, Filter filter, int limit) {
        StringBuilder where = new StringBuilder("source!='sample'"); List<String> args = new ArrayList<>();
        switch(filter.type()) {
            case "Spending" -> where.append(" AND direction='debit' AND category NOT IN ('Not a transaction','Payment receipt') AND (category NOT IN ('Transfer','Cash withdrawal') OR id IN (SELECT id FROM payment_meaning)) AND COALESCE((SELECT share FROM payment_meaning p WHERE p.id=movements.id),amount)>0");
            case "Money in" -> where.append(" AND direction='credit' AND category!='Not a transaction'");
            case "Money out" -> where.append(" AND direction='debit' AND category NOT IN ('Not a transaction','Payment receipt')");
            case "Transfers" -> where.append(" AND (category='Transfer' OR id IN (SELECT id FROM payment_meaning WHERE kind='transfer'))");
            case "Cash" -> where.append(" AND category='Cash withdrawal'");
            case "Refunds" -> where.append(" AND (category='Refund' OR id IN (SELECT id FROM payment_meaning WHERE kind='refund'))");
            case "Plan allocation" -> where.append(" AND direction='debit' AND category NOT IN ('Transfer','Cash withdrawal','Not a transaction','Payment receipt') AND id NOT IN (SELECT id FROM payment_meaning)");
            case "Needs review" -> where.append(" AND reviewed=0 AND category NOT IN ('Not a transaction','Payment receipt')");
            case "Excluded" -> where.append(" AND category='Not a transaction'");
            case "Receipts" -> where.append(" AND category='Payment receipt'");
            default -> where.append(" AND category!='Not a transaction'");
        }
        if(filter.account()!=null) {where.append(" AND issuer=? AND account=?");args.add(filter.account().issuer());args.add(filter.account().mask());}
        if(filter.from()>0) {where.append(" AND time>=?");args.add(""+filter.from());}
        if(filter.until()>0) {where.append(" AND time<?");args.add(""+filter.until());}
        if(!filter.category().isEmpty()) {where.append(" AND category=?");args.add(filter.category());}
        if(!filter.query().isBlank()) {where.append(" AND (merchant LIKE ? ESCAPE '\\' OR reference LIKE ? ESCAPE '\\')");String term="%"+filter.query().trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";args.add(term);args.add(term);}
        int count;
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM movements WHERE "+where,args.toArray(new String[0]))) {c.moveToFirst();count=c.getInt(0);}
        List<JSONObject> rows=new ArrayList<>();args.add(""+Math.min(limit,10000));
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT data,(SELECT share FROM payment_meaning p WHERE p.id=movements.id),(SELECT kind FROM payment_meaning p WHERE p.id=movements.id),(SELECT bucket FROM payment_meaning p WHERE p.id=movements.id) FROM movements WHERE "+where+" ORDER BY time DESC,id LIMIT ?",args.toArray(new String[0]))) {while(c.moveToNext()){JSONObject row=LedgerDb.object(c.getString(0));if(!c.isNull(1))try{row.put("personalSharePaise",c.getLong(1)).put("confirmedPurpose",c.getString(2)).put("confirmedBucket",c.getString(3));}catch(JSONException e){throw new IllegalStateException(e);}rows.add(row);}}
        return new Payments(rows,count);
    }
    static JSONObject chatFacts(Overview o, long target) throws JSONException {
        return new JSONObject().put("period",o.period.mode).put("recordedOutflowPaise",o.spent).put("previousOutflowPaise",o.previousSpent)
                .put("moneyInPaise",o.moneyIn).put("cashPaise",o.cash).put("transfersPaise",o.transfers).put("entries",o.entries)
                .put("previousEntries",o.priorEntries).put("unreviewed",o.unreviewed).put("targetPaise",target);
    }
}
