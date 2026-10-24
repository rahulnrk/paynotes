package in.prardhana.app;
import android.app.job.*;
public class PilotJob extends JobService {
    @Override public boolean onStartJob(JobParameters p){new Thread(()->{boolean retry=false;try{PilotSync.sync(getApplicationContext());}catch(Exception e){retry=true;}jobFinished(p,retry&&Store.prefs(this).getBoolean("pilot_consent",false));},"pilot-sync").start();return true;}
    @Override public boolean onStopJob(JobParameters p){return Store.prefs(this).getBoolean("pilot_consent",false);}
}
