package in.prardhana.app;

import android.content.*;
import org.json.*;
import java.time.*;
import java.util.*;

final class Store {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("pilot", 0); }
    static synchronized JSONArray rows(Context c) { return LedgerDb.get(c).rows(); }
    static synchronized String capture(Context c,String text,String source,long time){return capture(c,text,source,time,null,0);}
    static synchronized String capture(Context c,String text,String source,long time,String sender,long sentAt){
        FinancialEvents.observe(c,text,sender,source,time,sentAt);
        TransactionParser.Parsed p=SourcePolicy.parse(text,sender,source);
        Learning.observe(c,text,sender,p,canonical(text,source,sender,sentAt).isEmpty()?fingerprint(text,time):canonical(text,source,sender,sentAt));
        if(p==null)return "No completed transaction found. Check pending events or report an extraction issue.";
        try{
            String hash=fingerprint(text,time),canonical=canonical(text,source,sender,sentAt);
            LedgerDb db=LedgerDb.get(c);JSONObject facts=new JSONObject().put("source",source).put("time",time);
            FinancialEvents.facts(facts,text,sentAt,sender);facts.put("shape",MessagePatterns.shape(text));if(!Issuer.fromSender(sender).isEmpty())facts.put("issuer",Issuer.fromSender(sender));
            JSONObject row=db.byHash(hash);if(row==null&&!canonical.isEmpty())row=db.byHash(canonical);
            if(row==null)row=referenceMatch(db,p,facts);
            boolean paired=false;if(row==null){row=pairedAlert(db,p,facts);paired=row!=null;}
            if(row!=null){
                if(paired){if(!row.optBoolean("reviewed")&&row.optString("format").equals("core-ledger"))applyParsed(c,row,p);row.put("paired",true);db.put(row);}
                else if(!row.optBoolean("reviewed")&&row.optString("merchant").equals("Unknown merchant")&&!p.merchant().equals("Unknown merchant")&&!p.reference().isEmpty()){applyParsed(c,row,p);row.put("nameSource","Matched payment reference");db.put(row);}
                db.alias(hash,row.optString("id"));db.alias(canonical,row.optString("id"));
                prefs(c).edit().putInt("duplicates",prefs(c).getInt("duplicates",0)+1).apply();return "Duplicate ignored";
            }
            row=facts.put("id",UUID.randomUUID().toString()).put("fingerprint",hash).put("canonical",canonical).put("reviewed",false);
            applyParsed(c,row,p);db.put(row);FinancialEvents.reconcile(c,row);BudgetWidget.refresh(c);
            return "Transaction added · review the merchant and category";
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static String canonical(String text,String source,String sender,long sentAt){return source.equals("sms")&&sentAt>0?fingerprint((sender==null?"":sender.toUpperCase(Locale.ROOT))+"|"+text.replaceAll("\\s+"," ").trim(),sentAt):"";}
    private static JSONObject referenceMatch(LedgerDb db,TransactionParser.Parsed p,JSONObject facts){
        if(p.reference().isEmpty())return null;JSONArray candidates=db.byReference(p.reference(),p.paise(),p.direction());JSONObject match=null;boolean upi=p.reference().matches("[0-9]{12}");
        for(int i=0;i<candidates.length();i++){JSONObject r=candidates.optJSONObject(i);if(p.category().equals("Payment receipt")!=r.optString("category").equals("Payment receipt"))continue;
            if(!r.optString("channel").isEmpty()&&!r.optString("channel").equals("Bank / unspecified")&&!p.channel().equals("Bank / unspecified")&&!r.optString("channel").equals(p.channel()))continue;
            String account=r.optString("account"),other=facts.optString("account");if(!accountCompatible(account,other))continue;
            // A UPI app header (SBIUPI) can report another bank's account; the shared RRN and account then identify one movement.
            if(!compatible(r.optString("issuer"),facts.optString("issuer"))&&!(upi&&!account.isEmpty()&&!other.isEmpty()))continue;
            if(match!=null)return null;match=r;
        }return match;
    }
    static final long PAIR_WINDOW=60*60*1000L;
    /** A branch-ledger copy repeats a UPI alert without its reference: pair them one-to-one on bank, account, amount and time. */
    private static JSONObject pairedAlert(LedgerDb db,TransactionParser.Parsed p,JSONObject facts){return pairedAlert(db,p.paise(),p.direction(),p.format().equals("core-ledger"),facts);}
    private static JSONObject pairedAlert(LedgerDb db,long amount,String direction,boolean ledger,JSONObject facts){
        String issuer=facts.optString("issuer"),account=facts.optString("account");if(issuer.isEmpty()||account.isEmpty())return null;
        long time=facts.optLong("time");JSONArray near=db.near(amount,direction,time-PAIR_WINDOW,time+PAIR_WINDOW);JSONObject best=null;
        for(int i=0;i<near.length();i++){JSONObject r=near.optJSONObject(i);
            if(r.optString("id").equals(facts.optString("id"))||r.optBoolean("paired")||ledger==r.optString("format").equals("core-ledger")||!issuer.equals(r.optString("issuer"))||r.optString("account").isEmpty()||!accountCompatible(account,r.optString("account")))continue;
            if(best==null||Math.abs(r.optLong("time")-time)<Math.abs(best.optLong("time")-time))best=r;
        }return best;
    }
    /**
     * Repairs rows stored by older parsers, which an import cannot fix by refreshing one message at a time:
     * word references (and the wrong merges they caused), unpaired branch-ledger copies, and one UPI RRN stored twice.
     * Runs inside the import transaction; returns the number of rows merged away.
     */
    static int repairStored(LedgerDb db)throws JSONException{
        int merged=0;JSONArray words=db.select("reference!='' AND reference NOT GLOB '*[0-9]*'");
        for(int i=0;i<words.length();i++){JSONObject r=words.getJSONObject(i);r.remove("reference");db.put(r);db.unalias(r);}
        JSONArray ledgers=db.select("data LIKE ?","%\"format\":\"core-ledger\"%");
        for(int i=0;i<ledgers.length();i++){JSONObject l=ledgers.getJSONObject(i);if(l.optBoolean("paired")||l.optBoolean("reviewed"))continue;
            JSONObject u=pairedAlert(db,l.optLong("paise"),l.optString("direction"),true,l);if(u==null)continue;
            u.put("paired",true);db.put(u);db.mergeInto(l,u.optString("id"));merged++;}
        JSONArray rrn=db.select("length(reference)=12 AND reference NOT GLOB '*[^0-9]*' AND reference IN (SELECT reference FROM movements GROUP BY reference,amount,direction HAVING COUNT(*)>1) ORDER BY time");
        Map<String,JSONObject> first=new HashMap<>();
        for(int i=0;i<rrn.length();i++){JSONObject r=rrn.getJSONObject(i);String key=r.optString("reference")+"|"+r.optLong("paise")+"|"+r.optString("direction");JSONObject keep=first.get(key);
            if(keep==null){first.put(key,r);continue;}
            String a=keep.optString("account"),b=r.optString("account");
            if(a.isEmpty()||b.isEmpty()||!accountCompatible(a,b)||keep.optString("category").equals("Payment receipt")!=r.optString("category").equals("Payment receipt")||keep.optBoolean("reviewed")&&r.optBoolean("reviewed"))continue;
            if(r.optBoolean("reviewed")){first.put(key,r);db.mergeInto(keep,r.optString("id"));}else db.mergeInto(r,keep.optString("id"));merged++;}
        return merged;
    }
    static boolean compatible(String a,String b){return a.isEmpty()||b.isEmpty()||a.equalsIgnoreCase(b);}
    /** Banks mask to different lengths, so "Account ••080" and "Account ••8080" can be one account; cards and accounts never match. */
    static boolean accountCompatible(String a,String b){
        if(a.isEmpty()||b.isEmpty())return true;
        String da=a.replaceAll("\\D",""),db=b.replaceAll("\\D","");
        return a.replaceAll("[^A-Za-z]","").equalsIgnoreCase(b.replaceAll("[^A-Za-z]",""))&&Math.min(da.length(),db.length())>=3&&(da.endsWith(db)||db.endsWith(da));
    }
    static String fingerprint(String text,long time){try{return android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest((time+"|"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static void applyParsed(Context c,JSONObject row,TransactionParser.Parsed p) throws JSONException {
        boolean keepMatched=!row.optBoolean("reviewed")&&row.optString("nameSource").equals("Matched payment reference")&&p.merchant().equals("Unknown merchant");
        String savedName=row.optString("merchant"),savedRaw=row.optString("rawMerchant"),savedCategory=row.optString("category"),savedVpa=row.optString("vpa");
        if(!row.optBoolean("reviewed")){
            JSONObject rule=MerchantRules.find(c,p.vpa(),p.merchant(),row.optString("issuer"),row.optString("source"),p.channel(),p.direction());
            row.put("paise",p.paise()).put("direction",p.direction()).put("rawMerchant",p.merchant()).put("merchant",rule!=null?rule.optString("name",p.merchant()):p.merchant()).put("category",rule!=null?rule.optString("category",p.category()):p.category()).put("nameSource",rule!=null?"Your saved rule":p.nameSource()).put("categorySource",rule!=null?"Your saved rule":p.category().equals("Other")?"Needs review":"Local extraction");
        }
        row.put("type",p.type()).put("channel",p.channel()).put("vpa",p.vpa()).put("parserVersion",TransactionParser.VERSION);if(!p.reference().isEmpty())row.put("reference",p.reference());
        if(p.format().isEmpty())row.remove("format");else row.put("format",p.format());
        if(keepMatched){row.put("merchant",savedName).put("rawMerchant",savedRaw).put("nameSource","Matched payment reference");if(p.direction().equals("debit"))row.put("category",savedCategory);if(p.vpa().isEmpty())row.put("vpa",savedVpa);}
    }
    record Candidate(TransactionParser.Parsed parsed,String fingerprint,long time,String text,long sentAt,String sender){Candidate(TransactionParser.Parsed p,String f,long t){this(p,f,t,"",0,null);}Candidate(TransactionParser.Parsed p,String f,long t,String body,long sent){this(p,f,t,body,sent,null);}}
    record Batch(int added,int duplicates,int refreshed,int excluded){}
    static synchronized Batch importBatch(Context c,List<Candidate> input,long generation){
        if(!prefs(c).getBoolean("sms_capture",false)||prefs(c).getLong("generation",0)!=generation)throw new IllegalStateException("Import stopped");
        LedgerDb db=LedgerDb.get(c);android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();int added=0,duplicates=0,refreshed=0,excluded=0;
        sql.beginTransaction();try{
            duplicates+=repairStored(db);
            for(Candidate candidate:input){
                JSONObject row=db.byHash(candidate.fingerprint);String canonical=canonical(candidate.text,"sms",candidate.sender,candidate.sentAt);
                if(row==null&&!canonical.isEmpty())row=db.byHash(canonical);
                FinancialEvents.observe(c,candidate.text,candidate.sender,"sms",candidate.time,candidate.sentAt);
                Learning.observe(c,candidate.text,candidate.sender,candidate.parsed,canonical.isEmpty()?candidate.fingerprint:canonical);
                TransactionParser.Parsed p=candidate.parsed;
                if(p==null){if(row!=null&&!row.optBoolean("reviewed")){if(!row.optString("category").equals("Not a transaction"))refreshed++;row.put("category","Not a transaction");db.put(row);excluded++;}continue;}
                JSONObject facts=new JSONObject().put("time",candidate.time).put("source","sms");if(!candidate.text.isEmpty()){FinancialEvents.facts(facts,candidate.text,candidate.sentAt,candidate.sender);facts.put("shape",MessagePatterns.shape(candidate.text));if(!Issuer.fromSender(candidate.sender).isEmpty())facts.put("issuer",Issuer.fromSender(candidate.sender));}
                if(row==null)row=referenceMatch(db,p,facts);
                boolean paired=false;if(row==null){row=pairedAlert(db,p,facts);paired=row!=null;}
                if(row!=null){duplicates++;String before=row.toString();
                    // Never let a branch-ledger copy overwrite the richer UPI alert it was paired with.
                    if(!(p.format().equals("core-ledger")&&!row.optString("format").equals("core-ledger")))applyParsed(c,row,p);if(paired)row.put("paired",true);
                    if(row.optString("fingerprint").equals(candidate.fingerprint)&&!candidate.text.isEmpty()){FinancialEvents.facts(row,candidate.text,candidate.sentAt,candidate.sender);row.put("canonical",canonical);}
                    if(!before.equals(row.toString())){db.put(row);refreshed++;}db.alias(candidate.fingerprint,row.optString("id"));db.alias(canonical,row.optString("id"));continue;}
                row=facts.put("id",UUID.randomUUID().toString()).put("fingerprint",candidate.fingerprint).put("canonical",canonical).put("reviewed",false);applyParsed(c,row,p);db.put(row);added++;
            }
            duplicates+=repairStored(db);db.trimEvents();sql.setTransactionSuccessful();
        }catch(JSONException e){throw new IllegalStateException(e);}finally{sql.endTransaction();}
        FinancialEvents.reconcileAll(c);BudgetWidget.refresh(c);return new Batch(added,duplicates,refreshed,excluded);
    }
    static synchronized int[] refreshImported(Context c,Map<String,TransactionParser.Parsed> parsed,long generation){
        if(!prefs(c).getBoolean("sms_capture",false)||prefs(c).getLong("generation",0)!=generation)return new int[]{0,0};
        JSONArray all=rows(c);int changed=0,excluded=0;
        for(int i=0;i<all.length();i++){JSONObject row=all.optJSONObject(i);String hash=row.optString("fingerprint");if(row.optBoolean("reviewed")||!parsed.containsKey(hash))continue;String before=row.toString();TransactionParser.Parsed fresh=parsed.get(hash);
            try{if(fresh==null){row.put("category","Not a transaction");excluded++;}else{row.put("direction",fresh.direction());if(!fresh.reference().isEmpty())row.put("reference",fresh.reference());if(!fresh.merchant().equals("Unknown merchant")){row.put("merchant",fresh.merchant());row.put("category",fresh.category());}}}catch(JSONException e){throw new IllegalStateException(e);}if(!before.equals(row.toString()))changed++;
        }
        if(changed>0){LedgerDb.get(c).save(all);BudgetWidget.refresh(c);}return new int[]{changed,excluded};
    }
    static long start(String period) {
        LocalDate day = LocalDate.now();
        if (period.equals("Weekly")) day = day.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (period.equals("Monthly")) day = day.withDayOfMonth(1);
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    static long spent(Context c, String period) {
        return LedgerDb.get(c).spent(start(period),System.currentTimeMillis());
    }
    static String money(long paise) { return java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(paise / 100.0); }
    static synchronized void edit(Context c, String id, String name, String category, boolean remove) {
        JSONArray old=rows(c), next=new JSONArray();
        for(int i=0;i<old.length();i++) { JSONObject r=old.optJSONObject(i); if(r.optString("id").equals(id)) { if(remove) continue; try {r.put("merchant", name).put("category", category).put("reviewed",true);} catch(JSONException ignored){} } next.put(r); }
        if(remove)LedgerDb.get(c).remove(id);else LedgerDb.get(c).save(next);prefs(c).edit().putInt("corrections",prefs(c).getInt("corrections",0)+1).apply(); BudgetWidget.refresh(c);
    }
    static synchronized void review(Context c,String id,String name,String category,String direction,boolean remember){
        JSONArray all=rows(c);JSONObject selected=null;
        for(int i=0;i<all.length();i++)if(all.optJSONObject(i).optString("id").equals(id))selected=all.optJSONObject(i);
        if(selected==null)return;
        try{JSONObject before=new JSONObject(selected.toString());String vpa=selected.optString("vpa"),raw=selected.optString("rawMerchant",selected.optString("merchant"));String key=MerchantRules.key(vpa,raw);
            if(remember&&!key.isEmpty()){MerchantRules.remember(c,vpa,raw,name,category,selected.optString("issuer"),selected.optString("source"),selected.optString("channel"),direction);for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(sameReviewGroup(selected,r)&&r.optString("direction").equals(direction))r.put("merchant",name).put("category",category).put("nameSource","Your saved rule").put("categorySource","Your saved rule");}}
            selected.put("merchant",name).put("category",category).put("direction",direction).put("reviewed",true).put("nameSource","You confirmed").put("categorySource","You confirmed");
            Learning.correction(c,before,selected);
            LedgerDb.get(c).save(all);prefs(c).edit().putInt("corrections",prefs(c).getInt("corrections",0)+1).apply();BudgetWidget.refresh(c);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static boolean sameReviewGroup(JSONObject a,JSONObject b){
        String key=MerchantRules.key(a.optString("vpa"),a.optString("rawMerchant",a.optString("merchant")));
        return !key.isEmpty()&&!b.optBoolean("reviewed")&&a.optString("direction").equals(b.optString("direction"))&&a.optString("source").equals(b.optString("source"))&&a.optString("issuer").equals(b.optString("issuer"))&&a.optString("channel").equals(b.optString("channel"))&&key.equals(MerchantRules.key(b.optString("vpa"),b.optString("rawMerchant",b.optString("merchant"))))&&!b.optString("category").equals("Not a transaction")&&!b.optString("category").equals("Payment receipt");
    }
    static int matchingReviewCount(Context c,JSONObject selected){JSONArray all=rows(c);int n=0;for(int i=0;i<all.length();i++)if(sameReviewGroup(selected,all.optJSONObject(i)))n++;return n;}
    static synchronized void reviewMatching(Context c,JSONObject selected,String name,String category){
        JSONArray all=rows(c);try{for(int i=0;i<all.length();i++){JSONObject row=all.optJSONObject(i);if(sameReviewGroup(selected,row))row.put("merchant",name).put("category",category).put("reviewed",true).put("nameSource","You confirmed group").put("categorySource","You confirmed group");}LedgerDb.get(c).save(all);BudgetWidget.refresh(c);}catch(JSONException e){throw new IllegalStateException(e);}
    }
    static JSONObject metrics(Context c) {
        JSONObject out=new JSONObject(); JSONArray a=rows(c); int reviewed=0;
        for(int i=0;i<a.length();i++) if(a.optJSONObject(i).optBoolean("reviewed")) reviewed++;
        try { out.put("schema",1).put("exportedAt",System.currentTimeMillis()).put("participant",prefs(c).getString("participant", "anonymous")).put("transactions",a.length()).put("reviewed",reviewed).put("duplicates",prefs(c).getInt("duplicates",0)).put("corrections",prefs(c).getInt("corrections",0)).put("opens",prefs(c).getInt("opens",0)).put("budgetEnabled",prefs(c).getLong("budget",0)>0).put("helpful",prefs(c).getInt("helpful",0)).put("unhelpful",prefs(c).getInt("unhelpful",0)); } catch(JSONException ignored){}
        return out;
    }
}
