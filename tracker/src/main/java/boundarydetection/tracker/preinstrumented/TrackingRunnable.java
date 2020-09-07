package boundarydetection.tracker.preinstrumented;

import boundarydetection.tracker.AccessTracker;
import boundarydetection.tracker.tasks.Task;


public class TrackingRunnable implements Runnable {

    private final Runnable delegate;
    private final Task trackerTask;

    public TrackingRunnable(Runnable delegate, Task trackerTask) {
        this.delegate = delegate;
        this.trackerTask = trackerTask;
    }

    @Override
    public void run() {
        AccessTracker.join(trackerTask);
        try {
            delegate.run();
        } finally {
            AccessTracker.discard();
        }
    }
}
