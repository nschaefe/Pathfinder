package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//TODO Tracker output redirect and checking here
// Reports currently appear in testClient dir


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestRequestProcessing {

    private ExecutorService pool;

    @BeforeAll
    public void init() {
        pool = Executors.newFixedThreadPool(4);
        AccessTracker.startTracking();
    }

    @BeforeEach
    public void prepare() {
        AccessTracker.startTask();
    }

    @Test
    public void syncProcessing() throws ExecutionException, InterruptedException {
        pool.submit(new Request()).get();
    }

    @Test
    public void asyncProcessing() throws ExecutionException, InterruptedException {
        pool.submit(new Request());
    }

    @AfterEach
    public void close() {
        AccessTracker.stopTask();
    }

    @AfterAll
    public void shutdown() {
        pool.shutdown();
    }


    private class Request implements Runnable {

        public volatile boolean completed = false;
        public volatile boolean canceled = false;

        @Override
        public void run() {
            try {
                Thread.sleep(100);
                completed = true;
            } catch (InterruptedException e) {
                canceled = true;
                System.out.println("Operation canceled");
            }

        }
    }

}
