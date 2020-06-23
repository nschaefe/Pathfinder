package testcases;

import boundarydetection.tracker.AccessTracker;
import boundarydetection.tracker.tasks.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestTaskForkJoinDiscard extends TestBase {

    @Test
    public void testSimpleForkJoinDiscard() throws InterruptedException {

        RR r = new RR(AccessTracker.fork());
        Thread t1 = new Thread(r);
        t1.start();
        t1.join();

        AccessTracker.join(r.innerTask);
        System.out.print(r.inToOut1 + r.inToOut2);
    }

    @Test
    public void testOneOfTwoForkJoinPathCoverage() throws InterruptedException {

        RR r = new RR(AccessTracker.fork());
        Thread t1 = new Thread(r);
        t1.start();
        t1.join();

        RR r2 = new RR(AccessTracker.fork());
        Thread t2 = new Thread(r2);
        t2.start();
        t2.join();

        AccessTracker.join(r2.innerTask);
        System.out.print(r.inToOut1 + r.inToOut2 + r2.inToOut1);
        //t1 writes not covered

    }

    @Test
    public void testTwoOfTwoForkJoinPathCoverage() throws InterruptedException {

        RR r = new RR(AccessTracker.fork());
        Thread t1 = new Thread(r);
        t1.start();
        t1.join();

        RR r2 = new RR(AccessTracker.fork());
        Thread t2 = new Thread(r2);
        t2.start();
        t2.join();

        AccessTracker.join(r.innerTask);
        AccessTracker.join(r2.innerTask);
        System.out.print(r.inToOut1 + r.inToOut2 + r2.inToOut1);
        // both should be covered
    }

    @Test
    public void testUncoveredCrossCommunication() throws InterruptedException {

        Shared sh = new Shared();

        RRS r = new RRS(AccessTracker.fork(), sh);
        Thread t1 = new Thread(r);
        t1.start();
        t1.join();

        RRS r2 = new RRS(AccessTracker.fork(), sh);
        Thread t2 = new Thread(r2);
        t2.start();
        t2.join();

        AccessTracker.join(r.innerTask);
        AccessTracker.join(r2.innerTask);
    }

    @Test
    public void testMultiHop() throws InterruptedException {

        RR r = new RR(AccessTracker.fork()) {
            @Override
            public void doRun() {
                AccessTracker.join(outterTask);
                assertTrue(AccessTracker.hasTask());
                System.out.print(outToIn);
                inToOut1 = "";

                RR r = new RR(AccessTracker.fork());

                Thread t1 = new Thread(r);
                t1.start();
                assertDoesNotThrow(() -> t1.join());
                AccessTracker.join(r.innerTask);
                System.out.print(r.inToOut1);

                innerTask = AccessTracker.fork();
                AccessTracker.discard();
                assertTrue(!AccessTracker.hasTask());
            }
        };

        Thread t1 = new Thread(r);
        t1.start();
        t1.join();
        AccessTracker.join(r.innerTask);
        System.out.print(r.inToOut1);
        // All messages should be covered
    }

    @Test
    public void testDoubleJoin() throws InterruptedException {
        RR r = new RR(AccessTracker.fork()) {
            @Override
            public void doRun() {
                AccessTracker.join(outterTask);
                System.out.print(outToIn);
                inToOut1 = "";
                AccessTracker.join(outterTask);
                System.out.print(outToIn);
                innerTask = AccessTracker.fork();
                AccessTracker.discard();
            }
        };

        Thread t1 = new Thread(r);
        t1.start();
        t1.join();
        AccessTracker.join(r.innerTask);
        System.out.print(r.inToOut1);
    }

    @Test
    public void testWriteCapability() throws InterruptedException {
        AccessTracker.getTask().setWriteCapability(false);
        RR r = new RR(AccessTracker.fork()) {
            @Override
            public void doRun() {
                AccessTracker.join(outterTask);
                AccessTracker.getTask().setWriteCapability(true);
                System.out.print(outToIn);
                inToOut1 = "";
                AccessTracker.join(outterTask);
                System.out.print(outToIn);
                innerTask = AccessTracker.fork();
                AccessTracker.discard();
            }
        };

        Thread t1 = new Thread(r);
        t1.start();
        t1.join();
        AccessTracker.join(r.innerTask);
        System.out.print(r.inToOut1);
    }



    private class RR implements Runnable {

        public Task outterTask;
        public String outToIn;

        public Task innerTask;
        public String inToOut1;
        public String inToOut2;


        public RR(Task t) {
            outterTask = t;
            outToIn = "";
        }

        public void run() {
            assertDoesNotThrow(() -> doRun());
        }

        public void doRun() {
            AccessTracker.join(outterTask);
            AccessTracker.getTask().setWriteCapability(true);
            assertTrue(AccessTracker.hasTask());
            System.out.print(outToIn);

            inToOut1 = "";
            inToOut2 = "";
            innerTask = AccessTracker.fork();
            AccessTracker.discard();
            assertTrue(!AccessTracker.hasTask());
        }

    }

    private class RRS implements Runnable {

        private Task outterTask;
        public Task innerTask;

        private Shared shared;

        public RRS(Task t, Shared s) {
            outterTask = t;
            shared = s;
        }

        public RRS(Task t) {
            outterTask = t;
        }

        public void run() {
            assertDoesNotThrow(() -> doRun());
        }

        public void doRun() {
            AccessTracker.join(outterTask);
            assertTrue(AccessTracker.hasTask());

            synchronized (shared) {
                shared.mess1 = shared.mess1 + "1";
            }

            innerTask = AccessTracker.fork();
            AccessTracker.discard();
            assertTrue(!AccessTracker.hasTask());
        }

    }


    private class Shared {

        public String mess1;
        public String mess2;

    }


}
