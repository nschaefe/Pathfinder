package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TestConcurrentFieldAccess extends TestBase{

    @Test
    public void testMultipleConcurrentRequestsUnsafe() throws InterruptedException {
        ServerUnsafe s = new ServerUnsafe();
        for (int i = 0; i < 10; i++) {
            AccessTracker.startTask();

            s.addRequest();

            AccessTracker.stopTask();
        }
        s.join();
    }

    @Test
    public void testMultipleConcurrentRequestsAtomic() throws InterruptedException {
        ServerAtomic s = new ServerAtomic();
        for (int i = 0; i < 10; i++) {
            AccessTracker.startTask();

            s.addRequest();

            AccessTracker.stopTask();
        }
        s.join();
    }

    @Test
    public void testMultipleConcurrentRequestsLocking() throws InterruptedException {
        ServerLocking s = new ServerLocking();
        for (int i = 0; i < 10; i++) {
            AccessTracker.startTask();

            s.addRequest();

            AccessTracker.stopTask();
        }
        s.join();
    }

    @Test
    public void testMultipleConcurrentRequestsSynchronized() throws InterruptedException {
        ServerSynchronized s = new ServerSynchronized();
        for (int i = 0; i < 10; i++) {
            AccessTracker.startTask();

            s.addRequest();

            AccessTracker.stopTask();
        }
        s.join();
    }


    public static class ServerUnsafe {

        private int requestsCreated = 0;
        private int requestsStarted = 0;
        private int requestsCompleted = 0;

        private List<Thread> threads = new ArrayList<Thread>();

        private void log(String message) {
            System.out.println(message);
        }

        public void addRequest() {
            Thread t = new Thread(new Request());
            t.start();
            threads.add(t);
            requestsCreated++;
        }

        public void join() throws InterruptedException {
            for (int i = 0; i < threads.size(); i++) {
                threads.get(i).join();
            }
            log("Joined " + threads.size() + "/" + requestsCreated + " threads");
        }

        public class Request implements Runnable {

            public void run() {
                if (requestsStarted > requestsCompleted) {
                    log("Starting new request with " + (requestsStarted - requestsCompleted) + " ongoing requests");
                }
                requestsStarted++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Swallow interruption
                }
                requestsCompleted++;
                if (requestsStarted > requestsCompleted) {
                    log("Completed request with " + (requestsStarted - requestsCompleted) + " remaining requests");
                }
            }

        }

    }


    public static class ServerAtomic {

        private AtomicInteger requestsCreated = new AtomicInteger(0);
        private AtomicInteger requestsStarted = new AtomicInteger(0);
        private AtomicInteger requestsCompleted = new AtomicInteger(0);

        private List<Thread> threads = new ArrayList<Thread>();

        private void log(String message) {
            System.out.println(message);
        }

        public void addRequest() {
            Thread t = new Thread(new Request());
            t.start();
            threads.add(t);
            requestsCreated.incrementAndGet();
        }

        public void join() throws InterruptedException {
            for (int i = 0; i < threads.size(); i++) {
                threads.get(i).join();
            }
            log("Joined " + threads.size() + "/" + requestsCreated.get() + " threads");
        }

        public class Request implements Runnable {

            public void run() {
                int requestsStarted = ServerAtomic.this.requestsStarted.get();
                int requestsCompleted = ServerAtomic.this.requestsCompleted.get();
                if (requestsStarted > requestsCompleted) {
                    log("Starting new request with " + (requestsStarted - requestsCompleted) + " ongoing requests");
                }
                ServerAtomic.this.requestsStarted.incrementAndGet();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Swallow interruption
                }
                ServerAtomic.this.requestsCompleted.incrementAndGet();

                requestsStarted = ServerAtomic.this.requestsStarted.get();
                requestsCompleted = ServerAtomic.this.requestsCompleted.get();
                if (requestsStarted > requestsCompleted) {
                    log("Completed request with " + (requestsStarted - requestsCompleted) + " remaining requests");
                }
            }

        }

    }


    public static class ServerLocking {

        private Lock lock = new ReentrantLock();
        private int requestsCreated = 0;
        private int requestsStarted = 0;
        private int requestsCompleted = 0;

        private List<Thread> threads = new ArrayList<Thread>();

        private void log(String message) {
            System.out.println(message);
        }

        public void addRequest() {
            Thread t = new Thread(new Request());
            t.start();
            threads.add(t);
            lock.lock();
            try {
                requestsCreated++;
            } finally {
                lock.unlock();
            }
        }

        public void join() throws InterruptedException {
            for (int i = 0; i < threads.size(); i++) {
                threads.get(i).join();
            }
            log("Joined " + threads.size() + "/" + requestsCreated + " threads");
        }

        public class Request implements Runnable {

            public void run() {
                lock.lock();
                try {
                    if (requestsStarted > requestsCompleted) {
                        log("Starting new request with " + (requestsStarted - requestsCompleted) + " ongoing requests");
                    }
                    requestsStarted++;
                } finally {
                    lock.unlock();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Swallow interruption
                }
                lock.lock();
                try {
                    requestsCompleted++;
                    if (requestsStarted > requestsCompleted) {
                        log("Completed request with " + (requestsStarted - requestsCompleted) + " remaining requests");
                    }
                } finally {
                    lock.unlock();
                }
            }

        }

    }


    public static class ServerSynchronized {

        private int requestsCreated = 0;
        private int requestsStarted = 0;
        private int requestsCompleted = 0;

        private List<Thread> threads = new ArrayList<Thread>();

        private void log(String message) {
            System.out.println(message);
        }

        public void addRequest() {
            Thread t = new Thread(new Request());
            t.start();
            threads.add(t);
            synchronized(this) {
                requestsCreated++;
            }
        }

        public void join() throws InterruptedException {
            for (int i = 0; i < threads.size(); i++) {
                threads.get(i).join();
            }
            log("Joined " + threads.size() + "/" + requestsCreated + " threads");
        }

        public class Request implements Runnable {

            public void run() {
                synchronized(ServerSynchronized.this) {
                    if (requestsStarted > requestsCompleted) {
                        log("Starting new request with " + (requestsStarted - requestsCompleted) + " ongoing requests");
                    }
                    requestsStarted++;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Swallow interruption
                }
                synchronized(ServerSynchronized.this) {
                    requestsCompleted++;
                    if (requestsStarted > requestsCompleted) {
                        log("Completed request with " + (requestsStarted - requestsCompleted) + " remaining requests");
                    }
                }
            }

        }

    }
}
