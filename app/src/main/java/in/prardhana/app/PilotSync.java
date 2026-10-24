package in.prardhana.app;
import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** HTTPS, explicit enrollment and revocable consent. Background retry uses JobScheduler. */
final class PilotSync {
    static final int JOB=2606;
    static final class HttpError extends IOException {final int status;HttpError(int status){super("Server returned "+status+". Check enrollment or try later.");this.status=status;}}
    static String base(String value){try{URI u=new URI(value.trim());if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new Exception();return "https://"+u.getRawAuthority();}catch(Exception e){throw new IllegalArgumentException("Enter an HTTPS server address without a path");}}
    static JSONObject call(String endpoint,String token,String path,String method,JSONObject body)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(base(endpoint)+path).openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(12000);conn.setReadTimeout(15000);conn.setRequestMethod(method);conn.setRequestProperty("Content-Type","application/json");if(!token.isEmpty())conn.setRequestProperty("Authorization","Bearer "+token);
        try{if(body!=null){conn.setDoOutput(true);try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=conn.getResponseCode();if(status<200||status>=300)throw new HttpError(status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>192*1024)throw new IOException("Response too large");bytes.write(b,0,n);}}return new JSONObject(bytes.toString("UTF-8"));
        }finally{conn.disconnect();}
    }
    static synchronized void enroll(Context c,String url,String code)throws Exception{
        if(!Store.prefs(c).getBoolean("pilot_consent",false))throw new IllegalStateException("Consent required");
        String endpoint=base(url);String existing=SecureConfig.readNamed(c,"pilot_token");
        if(!existing.isEmpty()){try{call(endpoint,existing,"/v1/status","GET",null);Store.prefs(c).edit().putString("pilot_url",endpoint).commit();schedule(c);sync(c);return;}catch(HttpError error){if(error.status!=401)throw error;}}
        JSONObject r=call(endpoint,"","/v1/enroll","POST",new JSONObject().put("code",code.trim()));
        if(!Store.prefs(c).getBoolean("pilot_consent",false)){call(endpoint,r.getString("token"),"/v1/me","DELETE",null);return;}
        SecureConfig.saveNamed(c,"pilot_token",r.getString("token"));Store.prefs(c).edit().putString("pilot_url",endpoint).putString("pilot_installation",r.getString("installation")).commit();schedule(c);sync(c);
    }
    static void schedule(Context c){if(!Store.prefs(c).getBoolean("pilot_consent",false))return;JobScheduler j=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(j.getPendingJob(JOB)!=null)return;j.schedule(new JobInfo.Builder(JOB,new ComponentName(c,PilotJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(6*60*60*1000L).setPersisted(true).setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    static synchronized void sync(Context c)throws Exception{
        if(!Store.prefs(c).getBoolean("pilot_consent",false))return;String endpoint=Store.prefs(c).getString("pilot_url",""),token=SecureConfig.readNamed(c,"pilot_token");if(endpoint.isEmpty()||token.isEmpty())throw new IllegalStateException("Join the pilot first");
        long epoch=Store.prefs(c).getLong("pilot_epoch",0);JSONObject data=Learning.snapshot(c);if(!Store.prefs(c).getBoolean("pilot_consent",false))return;call(endpoint,token,"/v1/snapshot","POST",data);
        java.util.List<JSONObject> reports=new java.util.ArrayList<>();try(Cursor cur=LedgerDb.get(c).getReadableDatabase().rawQuery("SELECT data FROM reports LIMIT 100",null)){while(cur.moveToNext())reports.add(new JSONObject(cur.getString(0)));}
        for(JSONObject r:reports){if(!Store.prefs(c).getBoolean("pilot_consent",false)||epoch!=Store.prefs(c).getLong("pilot_epoch",0))return;call(endpoint,token,"/v1/report","POST",r);if(epoch==Store.prefs(c).getLong("pilot_epoch",0))LedgerDb.get(c).getWritableDatabase().delete("reports","id=?",new String[]{r.getString("id")});}
        if(!Store.prefs(c).getBoolean("pilot_consent",false)||epoch!=Store.prefs(c).getLong("pilot_epoch",0))return;JSONObject status=call(endpoint,token,"/v1/status","GET",null);if(epoch!=Store.prefs(c).getLong("pilot_epoch",0))return;Store.prefs(c).edit().putLong("pilot_last_sync",System.currentTimeMillis()).putString("pilot_status",status.toString()).apply();
    }
    static void pause(Context c){Store.prefs(c).edit().putBoolean("pilot_consent",false).putLong("pilot_epoch",Store.prefs(c).getLong("pilot_epoch",0)+1).commit();((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB);}
    static synchronized void removeRemote(Context c)throws Exception{pause(c);String endpoint=Store.prefs(c).getString("pilot_url",""),token=SecureConfig.readNamed(c,"pilot_token");if(!token.isEmpty())call(endpoint,token,"/v1/me","DELETE",null);pause(c);Store.prefs(c).edit().remove("pilot_token").remove("pilot_installation").remove("pilot_status").apply();LedgerDb.get(c).getWritableDatabase().delete("diagnostics",null,null);LedgerDb.get(c).getWritableDatabase().delete("reports",null,null);}
}
