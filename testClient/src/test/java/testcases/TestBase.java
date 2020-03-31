package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestBase {

    private OutputStream stream;

    @BeforeAll
    public void init() {
        System.out.println("Reads that happen after the actual tests (closing of pool)" +
                " can cause detections which are written to a closed stream.");
    }

    @BeforeEach
    public void prepare(TestInfo testInfo) throws IOException {
        Method method = testInfo.getTestMethod().get();
        //TODO with fileoutputstream: seems to lead to incomplete reports and sometimes hangs
        //stream = new FileOutputStream("./" + this.getClass().getSimpleName() + ':' + method.getName() + "_tracker_report.json");
        // AccessTracker.setDebugOutputStream(stream);

        AccessTracker.resetTracking();
        AccessTracker.startTask();
    }


    @AfterEach
    public void close() throws IOException, InterruptedException {
        AccessTracker.stopTask();
        // stream.close();
    }
}
