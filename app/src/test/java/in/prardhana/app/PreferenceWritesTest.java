package in.prardhana.app;

import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class PreferenceWritesTest {
    @Test public void refusesDiskBarrierOnUiThread(){
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences("barrier",0);
        assertThrows(IllegalStateException.class,()->PreferenceWrites.durable(prefs.edit().putBoolean("saved",true)));
        assertFalse(prefs.getBoolean("saved",false));
    }
    @Test public void workerWriteCompletesBeforeReturning()throws Exception{
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences("barrier",0);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try{worker.submit(()->PreferenceWrites.durable(prefs.edit().putBoolean("saved",true))).get(5,TimeUnit.SECONDS);assertTrue(prefs.getBoolean("saved",false));}finally{worker.shutdownNow();}
    }
    @Test public void diskFailureIsReported()throws Exception{
        var editor=(SharedPreferences.Editor)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(proxy,method,args)->method.getName().equals("commit")?false:proxy);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try{ExecutionException failure=assertThrows(ExecutionException.class,()->worker.submit(()->PreferenceWrites.durable(editor)).get(5,TimeUnit.SECONDS));assertTrue(failure.getCause() instanceof IllegalStateException);}finally{worker.shutdownNow();}
    }
}
