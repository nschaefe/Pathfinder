package client;

import boundarydetection.tracker.AccessTracker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.concurrent.*;

public class Client extends ClientBase {

    private Integer[] messages, m2;
    private ArrayDeque<Integer> q;
    private ArrayList<Integer> a;
    private ArrayBlockingQueue<Integer> b;
    private Integer i;
    private LinkedList<Integer> li;
    private PriorityQueue<Integer> rr;
    private LinkedBlockingQueue<Integer> bq;
    private ConcurrentLinkedQueue<Integer> cq;
    private ConcurrentLinkedDeque<Integer> dq;
    private ConcurrentHashMap<Integer, Integer> ch;

    private static Integer si;

    public Client() {
        super(42);
        messages = new Integer[2];
        q = new ArrayDeque<>();
        a = new ArrayList<>();
        b = new ArrayBlockingQueue<Integer>(5);
        i = new Integer(11);
        li = new LinkedList<>();
        rr = new PriorityQueue<>();
        bq = new LinkedBlockingQueue();
        cq = new ConcurrentLinkedQueue<>();
        dq = new ConcurrentLinkedDeque<>();
        ch = new ConcurrentHashMap<>();
    }

    public synchronized void write(int i) {
        Integer[] test = new Integer[1];
        test[0] = i;
        m2 = test;

        Integer[] m = messages;
        m[0] = i;

        this.i = i;

        li.add(i);

        si = i;

        Test.y = i;

        rr.add(i);

        bq.offer(i);

        a.add(i);
        q.add(i);

        cq.add(i);
        dq.add(i);
        ch.put(i, i);

    }

    public void noLambdaTest() throws InterruptedException {
        AccessTracker.startTask();
        double a = Math.random();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println(Client.this.li);
                System.out.println(a);
            }
        });
        t.start();
        t.join();
        AccessTracker.stopTask();

    }

    public void lambdaTest() throws InterruptedException {
        AccessTracker.startTask();
        double a = Math.random();
        Thread t = new Thread(() -> {
            System.out.println(this.li);
            System.out.println(a);
        });
        t.start();
        t.join();
        AccessTracker.stopTask();
    }

    public synchronized String read() {
        return "" + ch.get(42) + dq.poll() + cq.poll() + q.getFirst() + a.get(0) + m2[0] + bq.poll() + rr.peek() + Test.y + si + li.toArray().length + li.get(0) + messages[0] + this.i;
    }

    public synchronized void setNull() {
        messages[0] = null; // after set null no new read should be reported
    }

    public static void main(String[] args) {
        try {
            Test tt = new Test();
            Client c = new Client();
            c.lambdaTest();
            Thread t = new Thread(() -> {
                AccessTracker.startTask();
                // for (int i = 0; i < 10; i++) {
                // in iterations >1 less cases are detected, because on read side some reads refer to 0th item, but the added one is at the end.
                c.write(42);
                // double write should not be a new write
                // }
                AccessTracker.stopTask();
                // c.setNull(); //reads from null should not appear as detected case
            });
            t.start();
            t.join();

            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 1; i++) {
                    // reading from the same position written by the same writer from the same path should not detected twice
                    System.out.println("" + c.read());
                }
            });
            t2.start();
            t2.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}