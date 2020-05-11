package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.*;

import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestTaskInheritanceEventLogging extends TestBase {

    private int MAX_COUNT_BACKUP;

    @Override
    @BeforeEach
    public void prepare(TestInfo testInfo) throws IOException {
        super.prepare(testInfo);
        MAX_COUNT_BACKUP = AccessTracker.MAX_EVENT_COUNT;
        AccessTracker.enableEventLogging();
        AccessTracker.enableAutoTaskInheritance();
    }

    @Test
    public void limitedEventLogging() throws InterruptedException {
        AccessTracker.MAX_EVENT_COUNT = 7;
        String input = "mess";
        String input2 = "mess2";
        final String[] output = new String[1];
        Thread n = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    String s = "read" + input + input2;
                    AccessTracker.logEvent("event1");
                    AccessTracker.logEvent("event2");
                    AccessTracker.logEvent("event3");
                    AccessTracker.logEvent("event4");
                    AccessTracker.logEvent("event5");
                    output[0] = s;
                }
            }
        });
        n.start();
        n.join();
        System.out.println("read output" + output[0]);

    }

    @Override
    @AfterEach
    public void close(TestInfo testInfo) throws IOException, InterruptedException {
        AccessTracker.disableAutoTaskInheritance();
        AccessTracker.disableEventLogging();
        AccessTracker.MAX_EVENT_COUNT = MAX_COUNT_BACKUP;
        super.close(testInfo);
    }


}
