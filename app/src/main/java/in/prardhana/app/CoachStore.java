package in.prardhana.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.*;

/** Confirmed user context, separate from imported evidence. All callers use ledger workers. */
final class CoachStore {
    static JSONObject load(LedgerDb db) {
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT data FROM coach_state WHERE id=1",null)) {
            return c.moveToFirst()?LedgerDb.object(c.getString(0)):new JSONObject();
        }
    }
    static void save(LedgerDb db,JSONObject state) {
        db.getWritableDatabase().execSQL("INSERT OR REPLACE INTO coach_state(id,data) VALUES(1,?)",new Object[]{state.toString()});
    }
    record Snapshot(JSONObject state,long spent,long committed,long awaiting,int unknown,LocalDate start,LocalDate next,java.util.Map<String,Long> categories) {}
    static Snapshot snapshot(LedgerDb db,LocalDate today) {
        JSONObject state=load(db),p=state.optJSONObject("plan");
        int day=p==null?1:p.optInt("payday",1);
        LocalDate start=MoneyPlan.start(today,day),next=MoneyPlan.next(today,day);
        long since=start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end=today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long spent=0,committed=0,awaiting=0; int unknown=0;
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT m.amount,m.direction,m.category,p.share,p.bucket,p.kind,COALESCE((SELECT SUM(r.amount) FROM payment_meaning x JOIN movements r ON r.id=x.id WHERE x.linked=m.id AND x.kind='refund' AND r.direction='credit' AND r.category!='Not a transaction' AND r.source!='sample'),0) FROM movements m LEFT JOIN payment_meaning p ON p.id=m.id WHERE m.source!='sample' AND m.time>=? AND m.time<? AND m.category NOT IN ('Not a transaction','Payment receipt')",new String[]{""+since,""+end})) {
            while(c.moveToNext()) {
                if(!PaynotesData.spending(c.getString(1),c.getString(2)) && !("debit".equals(c.getString(1))&&!c.isNull(3)))continue;
                long amount=c.getLong(0),share=Math.max(0,(c.isNull(3)?amount:c.getLong(3))-c.getLong(6));
                if(c.isNull(3))unknown++;
                if("committed".equals(c.getString(4)))committed+=share;else spent+=share;
            }
        }
        // Receivables remain unavailable cash. Only explicitly linked credits settle them.
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT MAX(0,m.amount-p.share-COALESCE((SELECT SUM(r.amount) FROM payment_meaning x JOIN movements r ON r.id=x.id WHERE x.linked=m.id AND x.kind='reimbursement' AND r.direction='credit' AND r.source!='sample' AND r.category!='Not a transaction'),0)) FROM payment_meaning p JOIN movements m ON m.id=p.id WHERE p.kind='shared' AND m.direction='debit' AND m.source!='sample' AND m.category!='Not a transaction'",null)) {
            while(c.moveToNext())awaiting+=c.getLong(0);
        }
        return new Snapshot(state,spent,committed,awaiting,unknown,start,next,PaynotesData.overview(db,"Month",today,today.atTime(LocalTime.now()).atZone(ZoneId.systemDefault())).categories);
    }
    static long committedOverrun(Snapshot s) {
        JSONObject p=s.state.optJSONObject("plan");
        return p==null?0:Math.max(0,s.committed-p.optLong("fixed")-p.optLong("essentials")-p.optLong("debt")-p.optLong("reserve"));
    }
    static long remaining(Snapshot s) {JSONObject p=s.state.optJSONObject("plan");return p==null?0:MoneyPlan.flexible(p)-s.spent-committedOverrun(s);}
    static void meaning(LedgerDb db,String id,long share,String bucket,String kind,String linked) {
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try {
            JSONObject row=db.byId(id);if(row==null)throw new IllegalArgumentException("Payment no longer exists");
            if(share<0||share>row.optLong("paise"))throw new IllegalArgumentException("Share exceeds payment");
            if(!java.util.List.of("flexible","committed").contains(bucket)||!java.util.List.of("personal","shared","transfer","settlement","reimbursement","refund").contains(kind))throw new IllegalArgumentException();
            if(java.util.List.of("transfer","settlement","reimbursement","refund").contains(kind)&&share!=0)throw new IllegalArgumentException("Non-spending movements must have zero personal share");
            if(java.util.List.of("reimbursement","refund").contains(kind)) {
                JSONObject original=db.byId(linked);
                if(!row.optString("direction").equals("credit")||original==null||!original.optString("direction").equals("debit")||id.equals(linked))throw new IllegalArgumentException("Choose an original debit");
            } else if(!row.optString("direction").equals("debit")) throw new IllegalArgumentException("Choose a debit");
            if(java.util.List.of("reimbursement","refund").contains(kind)) {
                long capacity=0;
                try(Cursor c=sql.rawQuery("SELECT m.amount,p.share,p.kind FROM movements m LEFT JOIN payment_meaning p ON p.id=m.id WHERE m.id=?",new String[]{linked})){
                    c.moveToFirst();if(kind.equals("reimbursement")){if(!"shared".equals(c.getString(2)))throw new IllegalArgumentException("Mark the original payment as shared first");capacity=c.getLong(0)-c.getLong(1);}else capacity=c.isNull(1)?c.getLong(0):c.getLong(1);
                }
                try(Cursor c=sql.rawQuery("SELECT COALESCE(SUM(m.amount),0) FROM payment_meaning p JOIN movements m ON m.id=p.id WHERE p.linked=? AND p.kind=? AND p.id!=? AND m.direction='credit' AND m.category!='Not a transaction'",new String[]{linked,kind,id})){c.moveToFirst();capacity-=c.getLong(0);}
                if(row.optLong("paise")>capacity)throw new IllegalArgumentException("Credit exceeds the remaining amount; split or correct it before linking");
            } else {
                // Changing a split cannot invalidate already linked repayments/refunds.
                try(Cursor c=sql.rawQuery("SELECT p.kind,COALESCE(SUM(m.amount),0) FROM payment_meaning p JOIN movements m ON m.id=p.id WHERE p.linked=? AND m.direction='credit' AND m.category!='Not a transaction' GROUP BY p.kind",new String[]{id})){
                    while(c.moveToNext())if(c.getString(0).equals("reimbursement")?(!kind.equals("shared")||c.getLong(1)>row.optLong("paise")-share):c.getLong(1)>share)throw new IllegalArgumentException("Reset linked credits before changing this share");
                }
            }
            sql.execSQL("INSERT OR REPLACE INTO payment_meaning(id,share,bucket,kind,linked) VALUES(?,?,?,?,?)",new Object[]{id,share,bucket,kind,linked});
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    static JSONObject facts(Snapshot s) throws JSONException {
        JSONObject p=s.state.optJSONObject("plan"),g=s.state.optJSONObject("goal");
        JSONObject out=new JSONObject().put("planSet",p!=null).put("recordedFlexiblePaise",s.spent).put("recordedCommittedPaise",s.committed).put("awaitingRepaymentPaise",s.awaiting).put("unassignedPayments",s.unknown).put("cycleStart",s.start.toString()).put("nextPayday",s.next.toString());
        if(p!=null)out.put("plan",p).put("flexibleAllocationPaise",MoneyPlan.flexible(p)).put("committedOverrunPaise",committedOverrun(s)).put("remainingPlanPaise",remaining(s));
        if(g!=null)out.put("goal",g).put("confirmedGoalFundingPaise",MoneyPlan.funded(s.state)).put("requiredWeeklyPaise",MoneyPlan.weekly(s.state,LocalDate.now()));
        JSONArray categories=new JSONArray();JSONObject limits=s.state.optJSONObject("limits");java.util.Set<String> names=new java.util.TreeSet<>(s.categories.keySet());if(limits!=null)for(java.util.Iterator<String> it=limits.keys();it.hasNext();)names.add(it.next());
        for(String name:names){if(categories.length()==30)break;categories.put(new JSONObject().put("name",name).put("spentPaise",s.categories.getOrDefault(name,0L)).put("limitPaise",limits==null?0:limits.optLong(name)));}
        out.put("categories",categories);
        return out;
    }
}
