package testcases;

import boundarydetection.tracker.AccessTracker;

import java.util.function.Supplier;

public class Util {

    public static void asyncExecuteAndTrack(int count, Supplier<Runnable> runnableFactory) throws InterruptedException {

        Thread[] ths = new Thread[count];
        for (int i = 0; i < count; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    AccessTracker.startTask();
                    runnableFactory.get().run();
                    AccessTracker.stopTask();
                }
            });
            ths[i] = t;
            t.start();
        }

        for (Thread t : ths) {
            t.join();
        }
    }
}
