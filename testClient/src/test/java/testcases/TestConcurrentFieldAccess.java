package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class TestConcurrentFieldAccess {

    @Test
    public void testMultipleConcurrentRequests() throws InterruptedException {
        Server s = new Server();
        for (int i = 0; i < 10; i++) {
            AccessTracker.startTask();

            s.addRequest();

            AccessTracker.stopTask();
        }
        s.join();
    }


    public static class Server {

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
}
