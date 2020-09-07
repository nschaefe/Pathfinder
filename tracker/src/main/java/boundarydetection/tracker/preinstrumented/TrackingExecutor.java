package boundarydetection.tracker.preinstrumented;

import boundarydetection.tracker.AccessTracker;

import java.util.concurrent.Executor;


public class TrackingExecutor implements Executor {

    private final Executor delegate;

    public TrackingExecutor(Executor executor) {
        this.delegate = executor;
    }

    @Override
    public void execute(Runnable runnable) {
        Runnable r = AccessTracker.hasTask() ? new TrackingRunnable(runnable, AccessTracker.fork()) : runnable;
        delegate.execute(r);
    }

}
