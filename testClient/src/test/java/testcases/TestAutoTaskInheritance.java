package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.*;

import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestAutoTaskInheritance extends TestBase {

    @Test
    public void ineritToPresentTaskFail() throws InterruptedException {
        Util.asyncExecuteAndTrack(5, () -> new TestAutoTaskInheritance.workerRunnable());
    }

    private static boolean taskActive = false;

    @Test
    public void sampleLoop() throws InterruptedException {
        int count = 5;
        Thread[] ths = new Thread[count];
        for (int i = 0; i < count; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean hasTask = false;
                    synchronized (TestAutoTaskInheritance.class) {
                        if (!taskActive) {
                            taskActive = true;
                            hasTask = true;
                            AccessTracker.startTask();
                        }
                    }

                    (new TestAutoTaskInheritance.workerRunnable()).run();

                    synchronized (TestAutoTaskInheritance.class) {
                        if (hasTask) {
                            taskActive = false;
                            hasTask = false;
                            AccessTracker.stopTask();
                        }
                    }
                }
            });
            ths[i] = t;
            t.start();
        }

        for (Thread t : ths) {
            t.join();
        }
    }

    private static volatile String message;

    class workerRunnable implements Runnable {

        @Override
        public void run() {
            System.out.println(message);
            message = "my id: " + Thread.currentThread().getId();
        }
    }

    @BeforeEach
    public void prepare(TestInfo testInfo) throws IOException {
        super.prepare(testInfo);
        AccessTracker.stopTask();
        AccessTracker.enableAutoTaskInheritance();
    }


    @AfterEach
    public void close(TestInfo testInfo) throws IOException, InterruptedException {
        super.close(testInfo);
        AccessTracker.disableAutoTaskInheritance();
    }

}
