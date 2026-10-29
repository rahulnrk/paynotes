package in.prardhana.app;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Looper;

final class PreferenceWrites {
    private PreferenceWrites() {}

    /** Enrollment and reset must reach disk before the worker reports completion. */
    @SuppressLint("ApplySharedPref") // Intentional durability barrier; forbidden on the UI thread.
    static void durable(SharedPreferences.Editor editor) {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Durable preference writes require a worker");
        if(!editor.commit())throw new IllegalStateException("Could not persist settings");
    }
}
