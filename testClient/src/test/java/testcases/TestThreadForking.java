package testcases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestThreadForking extends TestBase {

    @Test
    public void forkJoinSingleLocalMessage() throws InterruptedException {
        // Thread n reads the input, input is set as global field in the runnable (see compiled code) and should be detected
        String input = "mess";
        final String[] output = new String[1];
        Thread n = new Thread(new Runnable() {
            @Override
            public void run() {
                String s = "read" + input;
                output[0] = s;
            }
        });
        n.start();
        n.join();
        System.out.println("read output" + output[0]);
    }

    @Test
    public void forkJoinMultiLocalMessage() throws InterruptedException {
        List<Thread> workers = new ArrayList<Thread>();
        String input = "mess";
        for (int c = 0; c < 5; c++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                        String s = "read" + input;
                }
            });
            t.start();
            workers.add(t);
        }

        for (Thread worker : workers) {
            worker.join();
        }
    }

}
