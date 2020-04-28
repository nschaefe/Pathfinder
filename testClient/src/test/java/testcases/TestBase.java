package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestBase {

    private OutputStream stream;

//    @BeforeAll
//    public void init() {
//        System.out.println("Reads that happen after the actual tests (closing of pool)" +
//                " can cause detections which are written to a closed stream.");
//    }

    @BeforeEach
    public void prepare(TestInfo testInfo) throws IOException {
        Method method = testInfo.getTestMethod().get();
        AccessTracker.log(this.getClass().getSimpleName() + ':' + method.getName() + " TEST START");
        //TODO with fileoutputstream: seems to lead to incomplete reports and sometimes hangs
        //stream = new FileOutputStream("./" + this.getClass().getSimpleName() + ':' + method.getName() + "_tracker_report.json");
        // AccessTracker.setDebugOutputStream(stream);

        AccessTracker.disableEventLogging();
        AccessTracker.resetTracking();
        AccessTracker.startTask();
    }


    @AfterEach
    public void close(TestInfo testInfo) throws IOException, InterruptedException {
        AccessTracker.stopTask();
        // stream.close();
    }
}
