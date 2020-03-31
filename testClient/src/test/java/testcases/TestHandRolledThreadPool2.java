package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TestHandRolledThreadPool2 {

    @Test
    public void testUnsafe() throws InterruptedException {
        ThreadPool p = new ThreadPool(3);
        for (int i = 0; i < 10; i++) {
            p.enqueue(new UnsafeRunnableForTest());
        }
        Thread.sleep(500);
        p.shutdown();
        p.join();
    }

    @Test
    public void testLocking() throws InterruptedException {
        ThreadPool p = new ThreadPool(3);
        for (int i = 0; i < 10; i++) {
            p.enqueue(new LockingRunnableForTest());
        }
        Thread.sleep(500);
        p.shutdown();
        p.join();
    }

    @Test
    public void testAtomic() throws InterruptedException {
        ThreadPool p = new ThreadPool(3);
        for (int i = 0; i < 10; i++) {
            p.enqueue(new AtomicRunnableForTest());
        }
        Thread.sleep(500);
        p.shutdown();
        p.join();
    }

    @Test
    public void testSynchronized() throws InterruptedException {
        ThreadPool p = new ThreadPool(3);
        for (int i = 0; i < 10; i++) {
            p.enqueue(new SynchronizedRunnableForTest());
        }
        Thread.sleep(500);
        p.shutdown();
        p.join();
    }

    public static void log(String message) {
//        System.out.println(message);
    }

    public static class UnsafeRunnableForTest implements Runnable {

        private static volatile String previousMessage = "";

        @Override
        public void run() {
            AccessTracker.startTask();

            log(previousMessage);
            previousMessage = "Hello from thread " + Thread.currentThread().getId();
            try {
                Thread.sleep(100);

                log(previousMessage);
                previousMessage = "Goodbye from thread " + Thread.currentThread().getId();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            AccessTracker.stopTask();
        }
    }

    public static class LockingRunnableForTest implements Runnable {

        private static Lock lock = new ReentrantLock();
        private static String previousMessage = "";

        @Override
        public void run() {
            AccessTracker.startTask();

            lock.lock();
            try {
                log(previousMessage);
                previousMessage = "Hello from thread " + Thread.currentThread().getId();
            } finally {
                lock.unlock();
            }
            try {
                Thread.sleep(100);

                lock.lock();
                try {
                    log(previousMessage);
                    previousMessage = "Goodbye from thread " + Thread.currentThread().getId();
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            AccessTracker.stopTask();
        }
    }

    public static class AtomicRunnableForTest implements Runnable {

        private static AtomicReference<String> previousMessage = new AtomicReference<>("");

        @Override
        public void run() {
            AccessTracker.startTask();

            log(previousMessage.getAndSet("Hello from thread " + Thread.currentThread().getId()));
            try {
                Thread.sleep(100);

                log(previousMessage.getAndSet("Goodbye from thread " + Thread.currentThread().getId()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            AccessTracker.stopTask();
        }
    }

    public static class SynchronizedRunnableForTest implements Runnable {

        private static volatile String previousMessage = "";

        @Override
        public void run() {
            AccessTracker.startTask();

            synchronized(SynchronizedRunnableForTest.class) {
                log(previousMessage);
                previousMessage = "Hello from thread " + Thread.currentThread().getId();
            }
            try {
                Thread.sleep(100);

                synchronized(SynchronizedRunnableForTest.class) {
                    log(previousMessage);
                    previousMessage = "Goodbye from thread " + Thread.currentThread().getId();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            AccessTracker.stopTask();
        }
    }


    public static class ThreadPool {

        private List<Worker> workers = new ArrayList<Worker>();
        private BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

        public ThreadPool(int numThreads) {
            for (int i = 0; i < numThreads; i++) {
                workers.add(new Worker());
            }
            for (Worker w : workers) {
                w.start();
            }
        }

        public boolean enqueue(Runnable r) {
            return queue.offer(r);
        }

        private Runnable dequeue() throws InterruptedException {
            return queue.take();
        }

        public void awaitEmpty() throws InterruptedException {
            while (!queue.isEmpty()) {
                Thread.sleep(100);
            }
        }

        public void shutdown() {
            for (Worker w : workers) {
                w.interrupt();
            }
        }

        public void join() throws InterruptedException {
            for (Worker w : workers) {
                w.join();
            }
        }

        public class Worker extends Thread {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Runnable next = dequeue();
                        next.run();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }

    }
}
