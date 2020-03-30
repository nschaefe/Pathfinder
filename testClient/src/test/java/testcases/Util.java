package testcases;

import java.util.function.Supplier;

public class Util {

    public static void asyncExecute(int count, Supplier<Runnable> runnableFactory) throws InterruptedException {

        Thread[] ths = new Thread[count];
        for (int i = 0; i < count; i++) {
            Thread t = new Thread(runnableFactory.get());
            ths[i] = t;
            t.start();
        }

        for (Thread t : ths) {
            t.join();
        }
    }
}
