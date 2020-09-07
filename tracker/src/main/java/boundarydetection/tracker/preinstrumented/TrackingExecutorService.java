package boundarydetection.tracker.preinstrumented;

import boundarydetection.tracker.AccessTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Executor which propagates the tracking task object from the {@link ExecutorService} caller side (producer) to the executor thread (consumer).
 */
public class TrackingExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    public TrackingExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        return delegate.awaitTermination(l, timeUnit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        Callable r = AccessTracker.hasTask() ? new TrackingCallable(callable, AccessTracker.fork()) : callable;
        return delegate.submit(r);
    }

    @Override
    public <T> Future<T> submit(Runnable runnable, T t) {
        Runnable r = AccessTracker.hasTask() ? new TrackingRunnable(runnable, AccessTracker.fork()) : runnable;
        return delegate.submit(r, t);
    }

    @Override
    public Future<?> submit(Runnable runnable) {
        Runnable r = AccessTracker.hasTask() ? new TrackingRunnable(runnable, AccessTracker.fork()) : runnable;
        return delegate.submit(r);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection)
            throws InterruptedException {
        return delegate.invokeAll(toTracking(collection));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection, long l,
                                         TimeUnit timeUnit) throws InterruptedException {
        return delegate.invokeAll(toTracking(collection), l, timeUnit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(toTracking(collection));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(toTracking(collection), l, timeUnit);
    }


    private <T> Collection<? extends Callable<T>> toTracking(Collection<? extends Callable<T>> delegate) {
        if (!AccessTracker.hasTask()) return delegate;

        List<Callable<T>> tracedCallables = new ArrayList<Callable<T>>(delegate.size());
        for (Callable<T> callable : delegate) {
            Callable r = new TrackingCallable(callable, AccessTracker.fork());
            tracedCallables.add(r);
        }

        return tracedCallables;
    }

    @Override
    public void execute(Runnable runnable) {
        Runnable r = AccessTracker.hasTask() ? new TrackingRunnable(runnable, AccessTracker.fork()) : runnable;
        delegate.execute(r);
    }
}
