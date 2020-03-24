package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.*;

//TODO Tracker output redirect and checking here
// Reports currently appear in testClient dir


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestRequestProcessingBase {

    ExecutorService pool;

    @BeforeAll
    public void init() {
        // return new ThreadPoolExecutor(4,4, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue());
        pool = getExecutorService();
    }

    abstract ExecutorService getExecutorService();

    @BeforeEach
    public void prepare() {
        AccessTracker.startTask();
    }

    @Test
    public void syncProcessingMulti() throws ExecutionException, InterruptedException {
        Collection<Future> flist = new ArrayList<Future>();
        for (int i = 0; i < 5; i++) {
            flist.add(pool.submit((Callable) new Request()));
        }
        for (Future f : flist) {
            f.get();
        }
    }

    @Test
    public void syncProcessing() throws ExecutionException, InterruptedException {
        // Single request submission does not use pool internal queues, direct hand over
        // The access to the result of the future through get() is not detected, because the writer of the result has
        // not a taskid.
        pool.submit((Callable) new Request()).get();
    }

    @Test
    public void asyncProcessing() throws ExecutionException, InterruptedException {
        pool.submit((Callable) new Request());
    }

    @AfterEach
    public void close() {
        AccessTracker.stopTask();
    }

    @AfterAll
    public void shutdown() {
        pool.shutdown();
    }


    private class Request implements Runnable, Callable<String> {

        public volatile boolean completed = false;
        public volatile boolean canceled = false;

        @Override
        public void run() {
            exec();
        }

        @Override
        public String call() throws Exception {
            return exec();
        }

        private String exec() {
            try {
                Thread.sleep(50);
                completed = true;
            } catch (InterruptedException e) {
                canceled = true;
                System.out.println("Operation canceled");
            }
            return "Result";
        }
    }

}
