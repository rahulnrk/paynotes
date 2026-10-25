package in.prardhana.app;

import java.util.concurrent.*;

/** A bounded queue keeps notification parsing and UI mutations off Android's main thread. */
final class LedgerWork {
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"ledger-work");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static boolean submit(Runnable task){try{worker.execute(task);return true;}catch(RejectedExecutionException e){return false;}}
}
