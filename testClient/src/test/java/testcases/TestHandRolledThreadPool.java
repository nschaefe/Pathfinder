package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestHandRolledThreadPool {

    private volatile String previousMessage = "";

    @Test
    public void testHandRolledThreadPool() throws InterruptedException {
        ThreadPool p = new ThreadPool(3);
        for (int i = 0; i < 10; i++) {
            AccessTracker.startTask();

            p.enqueue(new RunnableForTest());

            AccessTracker.stopTask();
        }
        Thread.sleep(500);
        p.shutdown();
        p.join();
    }

    public void log(String message) {
        System.out.println(message);
    }

    public class RunnableForTest implements Runnable {

        @Override
        public void run() {
            log(previousMessage);
            previousMessage = "Hello from thread " + Thread.currentThread().getId();
            try {
                Thread.sleep(100);

                log(previousMessage);
                previousMessage = "Goodbye from thread " + Thread.currentThread().getId();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public class ThreadPool {

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
