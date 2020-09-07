package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class TestThesisExecutionPatterns extends TestBase {

    @BeforeEach
    public void prepare(TestInfo testInfo) throws IOException {
        super.prepare(testInfo);
        AccessTracker.stopTask();
    }

    @Test
    public void consumerProducer() {

        String name = "";
        String[] args = {"ada"};

        BlockingQueue<Work> workQueue = new LinkedBlockingDeque<Work>();

        Thread producer = new Thread(() -> {
            AccessTracker.startTask();
            while (prodCon()) {
                doWork();
                // delegate work to other thread

                Work w = new Work(name, args);
                try {
                    workQueue.put(w);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                doMoreWork();
                w.await();
            }
            AccessTracker.stopTask();
        });
        producer.start();


        Thread consumer = new Thread(() -> {
            while (consCon()) {
                Work w = null;
                try {
                    w = workQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                process(w);
            }
        });
        consumer.start();
    }

    int p = 0;

    private boolean prodCon() {
        p++;
        return p <= 1;
    }

    int c = 0;

    private boolean consCon() {
        c++;
        return c <= 1;
    }

    private void doMoreWork() {
    }

    private void doWork() {

    }

    private void process(Work message) {
        message.run();
    }

    public class Work {

        public Work(String name, String[] args) {
            this.args = args;
            this.name = name;
        }

        private String[] args;
        private String name;

        public void run() {
            System.out.println(name + args);
        }

        public void await() {
        }
    }


}
