package in.prardhana.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;

/** Indexed, transactional local storage. Preferences remain settings only. */
final class LedgerDb extends SQLiteOpenHelper {
    private static LedgerDb instance;
    static synchronized LedgerDb get(Context c){if(instance==null)instance=new LedgerDb(c.getApplicationContext());return instance;}
    private LedgerDb(Context c){super(c,"ledger.db",null,2);setWriteAheadLoggingEnabled(true);migrate(c);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE movements(id TEXT PRIMARY KEY, time INTEGER NOT NULL, amount INTEGER NOT NULL, direction TEXT, reference TEXT COLLATE NOCASE, source TEXT, issuer TEXT, account TEXT, fingerprint TEXT, canonical TEXT, bodyHash TEXT, category TEXT, reviewed INTEGER, merchant TEXT, data TEXT NOT NULL)");
        db.execSQL("CREATE INDEX movement_time ON movements(time)");db.execSQL("CREATE INDEX movement_reference ON movements(reference,amount,direction)");db.execSQL("CREATE INDEX movement_hash ON movements(fingerprint)");db.execSQL("CREATE INDEX movement_canonical ON movements(canonical)");
        db.execSQL("CREATE TABLE evidence(hash TEXT PRIMARY KEY, movement TEXT NOT NULL)");
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY,time INTEGER NOT NULL,fingerprint TEXT,data TEXT NOT NULL)");db.execSQL("CREATE INDEX event_time ON events(time)");db.execSQL("CREATE INDEX event_fingerprint ON events(fingerprint)");
        db.execSQL("CREATE TABLE diagnostics(shape TEXT NOT NULL,issuer TEXT NOT NULL,header TEXT NOT NULL,outcome TEXT NOT NULL,count INTEGER NOT NULL, PRIMARY KEY(shape,issuer,header,outcome))");
        db.execSQL("CREATE TABLE diagnostic_seen(id TEXT PRIMARY KEY,time INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE reports(id TEXT PRIMARY KEY,data TEXT NOT NULL)");
        db.execSQL("CREATE TABLE tombstones(hash TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){
        if(old<2)db.execSQL("CREATE TABLE IF NOT EXISTS tombstones(hash TEXT PRIMARY KEY)");
    }
    // Added after v1 shipped; idempotent, so existing ledgers gain it without a schema version change.
    @Override public void onOpen(SQLiteDatabase db){if(!db.isReadOnly())db.execSQL("CREATE INDEX IF NOT EXISTS movement_body ON movements(bodyHash)");}
    private void migrate(Context c){
        SQLiteDatabase db=getWritableDatabase();try(Cursor cur=db.rawQuery("SELECT value FROM meta WHERE key='legacy_migrated'",null)){if(cur.moveToFirst()){Store.prefs(c).edit().remove("transactions").remove("financial_events").commit();return;}}
        db.beginTransaction();try{
            JSONArray rows=new JSONArray(Store.prefs(c).getString("transactions","[]"));for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(r.optString("id").isEmpty())throw new JSONException("Missing legacy identity");put(db,r);}
            JSONArray events=new JSONArray(Store.prefs(c).getString("financial_events","[]"));for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);event(db,e);}
            db.execSQL("INSERT INTO meta(key,value) VALUES('legacy_migrated','1')");db.setTransactionSuccessful();
        }catch(JSONException e){throw new IllegalStateException("Legacy data could not migrate; original preferences retained",e);}finally{db.endTransaction();}
        // Remove only after the SQLite transaction committed. A crash before this is safe to retry.
        Store.prefs(c).edit().remove("transactions").remove("financial_events").commit();
    }
    static JSONObject object(String value){try{return new JSONObject(value);}catch(JSONException e){throw new IllegalStateException("Corrupt local ledger",e);}}
    JSONArray rows(){JSONArray a=new JSONArray();try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements ORDER BY time,id",null)){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    JSONArray page(int limit,int offset){JSONArray a=new JSONArray();try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements ORDER BY time DESC,id LIMIT ? OFFSET ?",new String[]{String.valueOf(limit),String.valueOf(offset)})){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    private String filter(String queue){return switch(queue){case "Needs review"->"reviewed=0 AND category!='Not a transaction'";case "Missing name"->"merchant='Unknown merchant' AND category!='Not a transaction'";case "Uncategorised"->"category='Other'";case "Money in"->"direction='credit' AND category!='Not a transaction'";case "Money out"->"direction='debit' AND category!='Not a transaction'";case "Transfers"->"category='Transfer'";case "Refunds"->"category='Refund'";case "Receipts"->"category='Payment receipt'";case "Excluded"->"category='Not a transaction'";default->"1=1";};}
    JSONArray search(String queue,String term,int limit){JSONArray a=new JSONArray();String where=filter(queue)+(term.isEmpty()?"":" AND data LIKE ? ESCAPE '\\'");String[] args=term.isEmpty()?new String[]{String.valueOf(limit)}:new String[]{"%"+term.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%",String.valueOf(limit)};try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE "+where+" ORDER BY time DESC,id LIMIT ?",args)){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    int count(String queue,String term){String where=filter(queue)+(term.isEmpty()?"":" AND data LIKE ? ESCAPE '\\'");try(Cursor cur=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM movements WHERE "+where,term.isEmpty()?null:new String[]{"%"+term.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"})){cur.moveToFirst();return cur.getInt(0);}}
    JSONArray recent(long since,boolean reviewedOnly){JSONArray a=new JSONArray();try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE time>=?"+(reviewedOnly?" AND reviewed=1":"")+" ORDER BY time,id",new String[]{String.valueOf(since)})){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    JSONObject byHash(String hash){if(hash.isEmpty())return null;try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE fingerprint=? OR canonical=? OR id IN (SELECT movement FROM evidence WHERE hash=?) LIMIT 1",new String[]{hash,hash,hash})){return cur.moveToFirst()?object(cur.getString(0)):null;}}
    JSONArray byReference(String ref,long amount,String direction){JSONArray a=new JSONArray();if(ref.isEmpty())return a;try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE reference=? COLLATE NOCASE AND amount=? AND direction=?",new String[]{ref,String.valueOf(amount),direction})){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    JSONArray select(String where,String... args){JSONArray a=new JSONArray();try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE "+where,args)){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    /** Drops evidence merged into a row other than its own message, so those messages are imported again. */
    void unalias(JSONObject row){getWritableDatabase().delete("evidence","movement=? AND hash NOT IN (?,?)",new String[]{row.optString("id"),row.optString("fingerprint"),row.optString("canonical")});}
    /** Body matches only bridge SMS and an SMS app, once, with bank/account context and a unique candidate. */
    JSONObject byBody(String bodyHash,long time,long window,String source,String issuer,String account){
        if(bodyHash.isEmpty()||issuer.isEmpty()||account.isEmpty())return null;
        JSONObject match=null;
        try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE bodyHash=? AND time BETWEEN ? AND ? AND issuer=? AND account=?",new String[]{bodyHash,String.valueOf(time-window),String.valueOf(time+window),issuer,account})){
            while(cur.moveToNext()){
                JSONObject r=object(cur.getString(0));String other=r.optString("source");
                if(r.optBoolean("bodyCopy")||!(source.equals("sms")&&SourcePolicy.smsApp(other)||SourcePolicy.smsApp(source)&&other.equals("sms")))continue;
                if(match!=null)return null;match=r;
            }
        }
        return match;
    }
    JSONObject byId(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE id=?",new String[]{id})){return c.moveToFirst()?object(c.getString(0)):null;}}
    boolean deleted(String... hashes){for(String hash:hashes){if(hash.isEmpty())continue;try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM tombstones WHERE hash=?",new String[]{hash})){if(c.moveToFirst())return true;}}return false;}
    private void tombstone(String hash){if(hash.isEmpty())return;ContentValues v=new ContentValues();v.put("hash",hash);getWritableDatabase().insertWithOnConflict("tombstones",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    JSONArray near(long amount,String direction,long from,long to){JSONArray a=new JSONArray();try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM movements WHERE amount=? AND direction=? AND time BETWEEN ? AND ?",new String[]{String.valueOf(amount),direction,String.valueOf(from),String.valueOf(to)})){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    static void put(SQLiteDatabase db,JSONObject r){ContentValues v=new ContentValues();v.put("id",r.optString("id"));v.put("time",r.optLong("time"));v.put("amount",r.optLong("paise"));for(String k:new String[]{"direction","reference","source","issuer","account","fingerprint","canonical","bodyHash","category","merchant"})v.put(k,r.optString(k));v.put("reviewed",r.optBoolean("reviewed")?1:0);v.put("data",r.toString());if(db.insertWithOnConflict("movements",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IllegalStateException("Ledger write failed");}
    void put(JSONObject r){put(getWritableDatabase(),r);}
    void alias(String hash,String id){if(hash.isEmpty())return;ContentValues v=new ContentValues();v.put("hash",hash);v.put("movement",id);getWritableDatabase().insertWithOnConflict("evidence",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    void save(JSONArray rows){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<rows.length();i++)put(db,rows.optJSONObject(i));db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void remove(String id){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{JSONObject row=byId(id);if(row!=null){tombstone(row.optString("fingerprint"));tombstone(row.optString("canonical"));}try(Cursor c=db.rawQuery("SELECT hash FROM evidence WHERE movement=?",new String[]{id})){while(c.moveToNext())tombstone(c.getString(0));}db.delete("movements","id=?",new String[]{id});db.delete("evidence","movement=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    static void event(SQLiteDatabase db,JSONObject e){String id=e.optString("id",e.optString("fingerprint"));if(id.isEmpty())return;ContentValues v=new ContentValues();v.put("id",id);v.put("time",e.optLong("time"));v.put("fingerprint",e.optString("fingerprint"));v.put("data",e.toString());try(Cursor cur=db.rawQuery("SELECT id FROM events WHERE id=? OR fingerprint=? LIMIT 1",new String[]{id,e.optString("fingerprint")})){if(cur.moveToFirst())return;}db.insertOrThrow("events",null,v);}
    JSONArray events(){JSONArray a=new JSONArray();try(Cursor cur=getReadableDatabase().rawQuery("SELECT data FROM events ORDER BY time,id",null)){while(cur.moveToNext())a.put(object(cur.getString(0)));}return a;}
    void event(JSONObject e){event(getWritableDatabase(),e);if(!getWritableDatabase().inTransaction())trimEvents();}
    void updateEvent(JSONObject e){ContentValues v=new ContentValues();v.put("data",e.toString());getWritableDatabase().update("events",v,"id=?",new String[]{e.optString("id",e.optString("fingerprint"))});}
    void trimEvents(){getWritableDatabase().execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY time DESC LIMIT 2000)");}
    void clear(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String table:new String[]{"movements","events","evidence","diagnostics","diagnostic_seen","reports","tombstones"})db.delete(table,null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    long spent(long start,long end){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM movements WHERE time>=? AND time<=? AND direction='debit' AND source!='sample' AND category NOT IN ('Transfer','Not a transaction','Payment receipt','Cash withdrawal')",new String[]{String.valueOf(start),String.valueOf(end)})){c.moveToFirst();return c.getLong(0);}}
    record Outflow(long moneyOut,long cash,long transfers,long remaining){}
    record MonthSummary(long moneyIn,Outflow outflow,int entries,int unreviewed){}
    MonthSummary month(long start,long end){
        long in=0;int entries=0,unreviewed=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT direction,reviewed,COUNT(*),COALESCE(SUM(amount),0) FROM movements WHERE time>=? AND time<=? AND source!='sample' AND category NOT IN ('Not a transaction','Payment receipt') GROUP BY direction,reviewed",new String[]{String.valueOf(start),String.valueOf(end)})){
            while(c.moveToNext()){entries+=c.getInt(2);if(c.getInt(1)==0)unreviewed+=c.getInt(2);if(c.getString(0).equals("credit"))in+=c.getLong(3);}
        }
        return new MonthSummary(in,outflow(start,end),entries,unreviewed);
    }
    JSONArray priorityReview(long start,long end){
        return select("time>=? AND time<=? AND reviewed=0 AND source!='sample' AND category NOT IN ('Not a transaction','Payment receipt') ORDER BY amount DESC,time DESC LIMIT 5",String.valueOf(start),String.valueOf(end));
    }
    Outflow outflow(long start,long end){
        long total=0,cash=0,transfers=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT category,COALESCE(SUM(amount),0) FROM movements WHERE time>=? AND time<=? AND direction='debit' AND source!='sample' AND category NOT IN ('Not a transaction','Payment receipt') GROUP BY category",new String[]{String.valueOf(start),String.valueOf(end)})){
            while(c.moveToNext()){long n=c.getLong(1);total+=n;if(c.getString(0).equals("Cash withdrawal"))cash+=n;else if(c.getString(0).equals("Transfer"))transfers+=n;}
        }
        return new Outflow(total,cash,transfers,total-cash-transfers);
    }

}
