package in.prardhana.app;

import android.content.*;
import org.json.*;
import java.time.*;
import java.util.*;

final class Store {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("pilot", 0); }
    static JSONArray rows(Context c) { return LedgerDb.get(c).rows(); }
    static synchronized String capture(Context c,String text,String source,long time){return capture(c,text,source,time,null,0);}
    /** Event, diagnostics, movement, identities and reconciliation commit together; the widget refreshes only after commit. */
    static synchronized String capture(Context c,String text,String source,long time,String sender,long sentAt){
        // A disallowed source leaves no trace: no event, no diagnostic pattern, no movement.
        if(!SourcePolicy.allowed(source))return "No completed transaction found. This app is not a supported payment source.";
        android.database.sqlite.SQLiteDatabase sql=LedgerDb.get(c).getWritableDatabase();String result;
        sql.beginTransaction();try{result=record(c,text,source,time,sender,sentAt);LedgerDb.get(c).trimEvents();sql.setTransactionSuccessful();}finally{sql.endTransaction();}
        if(result.startsWith("Transaction added"))BudgetWidget.refresh(c);
        return result;
    }
    private static String record(Context c,String text,String source,long time,String sender,long sentAt){
        FinancialEvents.observe(c,text,sender,source,time,sentAt);
        TransactionParser.Parsed p=SourcePolicy.parse(text,sender,source);
        Learning.observe(c,text,sender,p,canonical(text,source,sender,sentAt).isEmpty()?fingerprint(text,time):canonical(text,source,sender,sentAt));
        if(p==null)return "No completed transaction found. Check pending events or report an extraction issue.";
        try{
            String hash=fingerprint(text,time),canonical=canonical(text,source,sender,sentAt);
            LedgerDb db=LedgerDb.get(c);if(db.deleted(hash,canonical))return "Deleted entry ignored";JSONObject facts=new JSONObject().put("source",source).put("time",time);
            FinancialEvents.facts(facts,text,sentAt,sender);facts.put("shape",MessagePatterns.shape(text));if(!Issuer.fromSender(sender).isEmpty())facts.put("issuer",Issuer.fromSender(sender));
            String body=bodyKey(text,source);if(!body.isEmpty())facts.put("bodyHash",body);
            JSONObject row=existingEvidence(db,hash,canonical);
            if(row==null)row=referenceMatch(db,p,facts);
            boolean copy=false;if(row==null){row=db.byBody(body,time,COPY_WINDOW,source,facts.optString("issuer"),facts.optString("account"));copy=row!=null;}
            if(row!=null){
                if(copy){row.put("bodyCopy",true);db.put(row);}
                if(copy&&source.equals("sms")&&!row.optString("source").equals("sms")){smsReplacesNotification(c,row,p,text,sentAt,sender,canonical);db.put(row);}
                else if(!row.optBoolean("reviewed")&&row.optString("merchant").equals("Unknown merchant")&&!p.merchant().equals("Unknown merchant")&&!p.reference().isEmpty()){applyParsed(c,row,p);row.put("nameSource","Matched payment reference");db.put(row);}
                db.alias(hash,row.optString("id"));db.alias(canonical,row.optString("id"));
                prefs(c).edit().putInt("duplicates",prefs(c).getInt("duplicates",0)+1).apply();return "Duplicate ignored";
            }
            row=facts.put("id",UUID.randomUUID().toString()).put("fingerprint",hash).put("canonical",canonical).put("reviewed",false);
            applyParsed(c,row,p);db.put(row);FinancialEvents.reconcile(c,row);
            return "Transaction added · review the merchant and category";
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static String canonical(String text,String source,String sender,long sentAt){return source.equals("sms")&&sentAt>0?fingerprint((sender==null?"":sender.toUpperCase(Locale.ROOT))+"|"+text.replaceAll("\\s+"," ").trim(),sentAt):"";}
    private static JSONObject existingEvidence(LedgerDb db,String hash,String canonical){
        if(!canonical.isEmpty()){JSONObject exact=db.byHash(canonical);if(exact!=null)return exact;}
        JSONObject legacy=db.byHash(hash);
        // Equal bodies can share an arrival millisecond; different network identities remain separate.
        if(legacy!=null&&!canonical.isEmpty()&&!legacy.optString("canonical").isEmpty()&&!canonical.equals(legacy.optString("canonical")))return null;
        return legacy;
    }
    static final long COPY_WINDOW=10*60*1000L;
    /**
     * The same bank SMS can arrive twice: from the SMS receiver or inbox and as an SMS-app notification, with different
     * timestamps and no shared reference (UCO, IDFC, card spends). Its normalised body, within minutes, identifies one movement.
     */
    static String bodyKey(String text,String source){return !text.isBlank()&&(source.equals("sms")||SourcePolicy.smsApp(source))?fingerprint(text.replaceAll("\\s+"," ").trim(),0):"";}
    /** The SMS carries the real sender and network time, so it becomes the record; reviewed edits stay. */
    private static void smsReplacesNotification(Context c,JSONObject row,TransactionParser.Parsed p,String text,long sentAt,String sender,String canonical)throws JSONException{
        if(!row.optBoolean("reviewed"))applyParsed(c,row,p);
        row.put("source","sms").put("canonical",canonical).put("bodyHash",bodyKey(text,"sms"));FinancialEvents.facts(row,text,sentAt,sender);
        if(!Issuer.fromSender(sender).isEmpty())row.put("issuer",Issuer.fromSender(sender));
    }
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
    /** Release incorrect legacy word-reference aliases once, without automatically merging stored rows. */
    static int repairStored(LedgerDb db)throws JSONException{
        JSONArray words=db.select("reference!='' AND reference NOT GLOB '*[0-9]*'");
        for(int i=0;i<words.length();i++){JSONObject r=words.getJSONObject(i);r.remove("reference");db.put(r);db.unalias(r);}
        return 0;
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
        row.put("type",p.type()).put("channel",p.channel()).put("vpa",p.vpa()).put("parserVersion",TransactionParser.VERSION);if(!p.reference().isEmpty())row.put("reference",p.reference());else row.remove("reference");
        if(p.format().isEmpty())row.remove("format");else row.put("format",p.format());
        if(keepMatched){row.put("merchant",savedName).put("rawMerchant",savedRaw).put("nameSource","Matched payment reference");if(p.direction().equals("debit"))row.put("category",savedCategory);if(p.vpa().isEmpty())row.put("vpa",savedVpa);}
    }
    record Candidate(TransactionParser.Parsed parsed,String fingerprint,long time,String text,long sentAt,String sender){Candidate(TransactionParser.Parsed p,String f,long t){this(p,f,t,"",0,null);}Candidate(TransactionParser.Parsed p,String f,long t,String body,long sent){this(p,f,t,body,sent,null);}}
    record Batch(int added,int duplicates,int refreshed,int excluded){}
    static synchronized Batch importBatch(Context c,List<Candidate> input,long generation){
        if(!prefs(c).getBoolean("sms_capture",false)||prefs(c).getLong("generation",0)!=generation)throw new IllegalStateException("Import stopped");
        LedgerDb db=LedgerDb.get(c);android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();int added=0,duplicates=0,refreshed=0,excluded=0;
        sql.beginTransaction();try{
            try(android.database.Cursor marker=sql.rawQuery("SELECT value FROM meta WHERE key='safe_identity_v8'",null)){
                if(!marker.moveToFirst()){
                    repairStored(db);
                    JSONArray pairedRows=db.select("data LIKE ?","%\"paired\":true%");
                    for(int i=0;i<pairedRows.length();i++){JSONObject old=pairedRows.getJSONObject(i);db.unalias(old);old.remove("paired");db.put(old);}
                    sql.execSQL("INSERT INTO meta(key,value) VALUES('safe_identity_v8','1')");
                }
            }
            for(Candidate candidate:input){
                String canonical=canonical(candidate.text,"sms",candidate.sender,candidate.sentAt);JSONObject row=existingEvidence(db,candidate.fingerprint,canonical);
                if(db.deleted(candidate.fingerprint,canonical)){excluded++;continue;}
                FinancialEvents.observe(c,candidate.text,candidate.sender,"sms",candidate.time,candidate.sentAt);
                Learning.observe(c,candidate.text,candidate.sender,candidate.parsed,canonical.isEmpty()?candidate.fingerprint:canonical);
                TransactionParser.Parsed p=candidate.parsed;
                if(p==null){if(row!=null&&!row.optBoolean("reviewed")){if(!row.optString("category").equals("Not a transaction"))refreshed++;row.put("category","Not a transaction");db.put(row);excluded++;}continue;}
                JSONObject facts=new JSONObject().put("time",candidate.time).put("source","sms");if(!candidate.text.isEmpty()){FinancialEvents.facts(facts,candidate.text,candidate.sentAt,candidate.sender);facts.put("shape",MessagePatterns.shape(candidate.text));if(!Issuer.fromSender(candidate.sender).isEmpty())facts.put("issuer",Issuer.fromSender(candidate.sender));}
                String body=bodyKey(candidate.text,"sms");if(!body.isEmpty())facts.put("bodyHash",body);
                if(row==null)row=referenceMatch(db,p,facts);
                    boolean copy=false;if(row==null){row=db.byBody(body,candidate.time,COPY_WINDOW,"sms",facts.optString("issuer"),facts.optString("account"));copy=row!=null;}
                if(row!=null){duplicates++;String before=row.toString();if(copy)row.put("bodyCopy",true);
                    // Never let a branch-ledger copy overwrite the richer UPI alert it was paired with.
                    if(row.optString("fingerprint").equals(candidate.fingerprint)||!p.reference().isEmpty()||copy)applyParsed(c,row,p);
                    if(copy&&!row.optString("source").equals("sms"))smsReplacesNotification(c,row,p,candidate.text,candidate.sentAt,candidate.sender,canonical);
                    if(row.optString("fingerprint").equals(candidate.fingerprint)&&!candidate.text.isEmpty()){FinancialEvents.facts(row,candidate.text,candidate.sentAt,candidate.sender);row.put("canonical",canonical).put("bodyHash",body);}
                    if(!before.equals(row.toString())){db.put(row);refreshed++;}db.alias(candidate.fingerprint,row.optString("id"));db.alias(canonical,row.optString("id"));continue;}
                row=facts.put("id",UUID.randomUUID().toString()).put("fingerprint",candidate.fingerprint).put("canonical",canonical).put("reviewed",false);applyParsed(c,row,p);db.put(row);added++;
            }
            db.trimEvents();FinancialEvents.reconcileAll(c);sql.setTransactionSuccessful();
        }catch(JSONException e){throw new IllegalStateException(e);}finally{sql.endTransaction();}
        BudgetWidget.refresh(c);return new Batch(added,duplicates,refreshed,excluded);
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
    /** ₹100 crore: well above any single message-reported movement, and safely inside a long. */
    static final long MAX_PAISE=100_000_000_000L;
    /** Rupees as typed ("1,250.5") to integer paise; -1 when it is not a positive amount with at most two decimals. */
    static long parseRupees(String typed){
        try{long p=new java.math.BigDecimal(typed.trim().replace(",","")).movePointRight(2).longValueExact();return p>0&&p<=MAX_PAISE?p:-1;}catch(RuntimeException e){return -1;}
    }
    static String money(long paise) { return java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(paise / 100.0); }
    static synchronized void edit(Context c, String id, String name, String category, boolean remove) {
        LedgerDb db=LedgerDb.get(c);JSONObject row=db.byId(id);if(row==null)return;
        if(remove)db.remove(id);else try{row.put("merchant",name).put("category",category).put("reviewed",true);db.put(row);}catch(JSONException e){throw new IllegalStateException(e);}
        prefs(c).edit().putInt("corrections",prefs(c).getInt("corrections",0)+1).apply();BudgetWidget.refresh(c);
    }
    private static JSONArray reviewCandidates(LedgerDb db,JSONObject selected){
        return db.select("reviewed=0 AND direction=? AND source=? AND issuer=? AND account=?",selected.optString("direction"),selected.optString("source"),selected.optString("issuer"),selected.optString("account"));
    }
    static void review(Context c,String id,String name,String category,String direction,boolean remember){review(c,id,name,category,direction,remember,0);}
    /**
     * paise &gt; 0 overrides the extracted amount. The first extracted value stays in originalPaise; re-import never replaces
     * a reviewed row's amount. Event resolutions that relied on this movement are re-checked in the same transaction.
     */
    static synchronized void review(Context c,String id,String name,String category,String direction,boolean remember,long paise){
        if(paise<0||paise>MAX_PAISE)throw new IllegalArgumentException("Amount out of range");
        LedgerDb db=LedgerDb.get(c);JSONObject selected=db.byId(id);if(selected==null)return;
        android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{JSONObject before=new JSONObject(selected.toString());String vpa=selected.optString("vpa"),raw=selected.optString("rawMerchant",selected.optString("merchant"));
            if(paise>0&&paise!=selected.optLong("paise")){
                if(!selected.has("originalPaise"))selected.put("originalPaise",selected.optLong("paise"));
                if(paise==selected.optLong("originalPaise")){selected.remove("originalPaise");selected.remove("amountSource");}else selected.put("amountSource","You corrected");
                selected.put("paise",paise);
            }
            if(remember&&!MerchantRules.key(vpa,raw).isEmpty()&&selected.optString("direction").equals(direction)){
                JSONArray group=reviewCandidates(db,selected);
                for(int i=0;i<group.length();i++){JSONObject r=group.getJSONObject(i);if(sameReviewGroup(selected,r)){r.put("merchant",name).put("category",category).put("nameSource","Your saved rule").put("categorySource","Your saved rule");db.put(r);}}
            }
            selected.put("merchant",name).put("category",category).put("direction",direction).put("reviewed",true).put("nameSource","You confirmed").put("categorySource","You confirmed");
            db.put(selected);db.releaseEvents(id);FinancialEvents.reconcile(c,selected);Learning.correction(c,before,selected);sql.setTransactionSuccessful();
        }catch(JSONException e){throw new IllegalStateException(e);}finally{sql.endTransaction();}
        if(remember)MerchantRules.remember(c,selected.optString("vpa"),selected.optString("rawMerchant"),name,category,selected.optString("issuer"),selected.optString("source"),selected.optString("channel"),direction);
        prefs(c).edit().putInt("corrections",prefs(c).getInt("corrections",0)+1).apply();BudgetWidget.refresh(c);
    }
    static boolean sameReviewGroup(JSONObject a,JSONObject b){
        String key=MerchantRules.key(a.optString("vpa"),a.optString("rawMerchant",a.optString("merchant")));
        return !key.isEmpty()&&!b.optBoolean("reviewed")&&a.optString("direction").equals(b.optString("direction"))&&a.optString("account").equals(b.optString("account"))&&a.optString("source").equals(b.optString("source"))&&a.optString("issuer").equals(b.optString("issuer"))&&a.optString("channel").equals(b.optString("channel"))&&key.equals(MerchantRules.key(b.optString("vpa"),b.optString("rawMerchant",b.optString("merchant"))))&&!b.optString("category").equals("Not a transaction")&&!b.optString("category").equals("Payment receipt");
    }
    static int matchingReviewCount(Context c,JSONObject selected){JSONArray all=reviewCandidates(LedgerDb.get(c),selected);int n=0;for(int i=0;i<all.length();i++)if(sameReviewGroup(selected,all.optJSONObject(i)))n++;return n;}
    static synchronized void reviewMatching(Context c,JSONObject selected,String name,String category){
        LedgerDb db=LedgerDb.get(c);JSONArray all=reviewCandidates(db,selected);android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i);if(sameReviewGroup(selected,row)){row.put("merchant",name).put("category",category).put("reviewed",true).put("nameSource","You confirmed group").put("categorySource","You confirmed group");db.put(row);}}sql.setTransactionSuccessful();}catch(JSONException e){throw new IllegalStateException(e);}finally{sql.endTransaction();}BudgetWidget.refresh(c);
    }
    static JSONObject metrics(Context c) {
        JSONObject out=new JSONObject(); JSONArray a=rows(c); int reviewed=0;
        for(int i=0;i<a.length();i++) if(a.optJSONObject(i).optBoolean("reviewed")) reviewed++;
        try { out.put("schema",1).put("exportedAt",System.currentTimeMillis()).put("participant",prefs(c).getString("participant", "anonymous")).put("transactions",a.length()).put("reviewed",reviewed).put("duplicates",prefs(c).getInt("duplicates",0)).put("corrections",prefs(c).getInt("corrections",0)).put("opens",prefs(c).getInt("opens",0)).put("budgetEnabled",prefs(c).getLong("budget",0)>0).put("helpful",prefs(c).getInt("helpful",0)).put("unhelpful",prefs(c).getInt("unhelpful",0)); } catch(JSONException ignored){}
        return out;
    }
}
