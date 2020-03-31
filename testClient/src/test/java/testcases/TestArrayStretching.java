package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestArrayStretching extends TestBase {

    private volatile ArrayList<Integer> list;

    public TestArrayStretching() {
        list = new ArrayList(5);
    }

    @Test
    public void arrayListCapacityExceed() throws InterruptedException {
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                doT1();
            }

            private void doT1() {
                AccessTracker.startTask();
                for (int i = 0; i < 5; i++) {
                    list.add(1);
                }
                AccessTracker.stopTask();
            }
        });
        t1.start();
        t1.join();

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                doT2();
            }

            private void doT2() {
                AccessTracker.startTask();
                list.add(1);
                AccessTracker.stopTask();
            }
        });
        t2.start();
        t2.join();

        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                doT3();
            }

            private void doT3() {
                AccessTracker.startTask();
                list.get(0);
                AccessTracker.stopTask();
            }
        });
        t3.start();
        t3.join();

    }


}
