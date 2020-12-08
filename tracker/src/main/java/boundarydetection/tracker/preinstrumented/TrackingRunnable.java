package boundarydetection.tracker.preinstrumented;

import boundarydetection.tracker.AccessTracker;
import boundarydetection.tracker.tasks.Task;

/* Wraps a runnable to propagate a task to run(). */
public class TrackingRunnable implements Runnable {

    private final Runnable delegate;
    private final Task trackerTask;
    private final String tag;

    public TrackingRunnable(Runnable delegate, Task trackerTask) {
        this(delegate, trackerTask, "TrackingRunnable");
    }

    public TrackingRunnable(Runnable delegate, Task trackerTask, String tag) {
        this.delegate = delegate;
        this.trackerTask = trackerTask;
        this.tag = tag;

    }

    @Override
    public void run() {
        AccessTracker.join(trackerTask, tag);
        try {
            delegate.run();
        } finally {
            AccessTracker.discard();
        }
    }
}
