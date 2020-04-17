package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.*;

import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestTaskInheritance extends TestBase {

    private int MAX_COUNT_BACKUP;

    @Override
    @BeforeEach
    public void prepare(TestInfo testInfo) throws IOException {
        MAX_COUNT_BACKUP = AccessTracker.MAX_EVENT_COUNT;
        super.prepare(testInfo);
    }

    @Test
    public void forkJoinSingleLocalMessage() throws InterruptedException {
        // Thread n reads the input, input is set as global field in the runnable (see compiled code) and should be detected
        AccessTracker.MAX_EVENT_COUNT = 7;
        String input = "mess";
        String input2 = "mess";
        final String[] output = new String[1];
        Thread n = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    String s = "read" + input + input2;
                    AccessTracker.logEvent("adadawdad");
                    AccessTracker.logEvent("gggdadawdad");
                    AccessTracker.logEvent("adadawdad");
                    AccessTracker.logEvent("adadawdad");
                    AccessTracker.logEvent("adadawdad");
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
        AccessTracker.MAX_EVENT_COUNT = MAX_COUNT_BACKUP;
        super.close(testInfo);
    }


}
