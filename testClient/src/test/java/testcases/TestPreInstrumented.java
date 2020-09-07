package testcases;

import boundarydetection.tracker.AccessTracker;
import boundarydetection.tracker.preinstrumented.TrackingExecutor;
import boundarydetection.tracker.preinstrumented.TrackingExecutorService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

public class TestPreInstrumented extends TestBase {


    @Test
    public void executorServiceSimple() throws ExecutionException, InterruptedException {
        AccessTracker.stopTask();
        ExecutorService s = new TrackingExecutorService(getThreadPool());

        AccessTracker.startTask();
        String m = "message";
        Future f = s.submit(() -> {
            System.out.println(m);
        });
        f.get();

    }

    @Test
    public void executorServiceMulti() throws ExecutionException, InterruptedException {
        AccessTracker.stopTask();
        ExecutorService s = new TrackingExecutorService(getThreadPool());

        AccessTracker.startTask();
        String m = "message";
        Future f = s.submit(() -> {
            System.out.println(m);
        });
        Future f2 = s.submit(() -> {
            System.out.println(m);
        });

        f.get();
        f2.get();

    }

    @Test
    public void executorSimple() throws ExecutionException, InterruptedException {
        AccessTracker.stopTask();
        Executor s = new TrackingExecutor(getThreadPool());

        AccessTracker.startTask();
        String m = "message";
        s.execute(() -> {
            System.out.println(m);
        });
    }

    private ThreadPoolExecutor getThreadPool(){
        return  new ThreadPoolExecutor(1,1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    }
}
