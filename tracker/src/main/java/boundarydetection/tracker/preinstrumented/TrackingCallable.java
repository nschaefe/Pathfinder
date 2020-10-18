package boundarydetection.tracker.preinstrumented;

import boundarydetection.tracker.AccessTracker;
import boundarydetection.tracker.tasks.Task;

import java.util.concurrent.Callable;


public class TrackingCallable<V> implements Callable<V> {

    private final Callable<V> delegate;
    private final Task trackerTask;

    public TrackingCallable(Callable<V> delegate, Task trackerTask) {
        this.delegate = delegate;
        this.trackerTask = trackerTask;
    }

    @Override
    public V call() throws Exception {
        AccessTracker.join(trackerTask,"TrackingCallable");
        try {
            return delegate.call();
        } finally {
            AccessTracker.discard();
        }
    }
}
