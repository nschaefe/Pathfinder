package boundarydetection.tracker.preinstrumented;

import boundarydetection.tracker.AccessTracker;
import boundarydetection.tracker.tasks.Task;

import java.util.concurrent.Callable;


public class TrackingCallable<V> implements Callable<V> {

    private final Callable<V> delegate;
    private final Task trackerTask;
    private final String tag;

    public TrackingCallable(Callable<V> delegate, Task trackerTask) {
        this(delegate, trackerTask, "TrackingCallable");
    }

    public TrackingCallable(Callable<V> delegate, Task trackerTask, String tag) {
        this.delegate = delegate;
        this.trackerTask = trackerTask;
        this.tag = tag;
    }

    @Override
    public V call() throws Exception {
        AccessTracker.join(trackerTask, tag);
        try {
            return delegate.call();
        } finally {
            AccessTracker.discard();
        }
    }
}
