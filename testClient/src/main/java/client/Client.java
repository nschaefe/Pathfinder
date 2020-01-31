package client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

public class Client extends ClientBase {

    private Integer[] messages;
    private ArrayDeque<Integer> q;
    private ArrayList<Integer> a;
    private ArrayBlockingQueue<Integer> b;
    private Integer i;
    private LinkedList<Integer> li;


    public Client() {
        super(42);
        messages = new Integer[2];
        q = new ArrayDeque<>();
        a = new ArrayList<>();
        b = new ArrayBlockingQueue<Integer>(5);
        i = new Integer(11);
        li = new LinkedList<>();
    }


    public synchronized void addMessageArr(int i) {
        Integer[] m = messages;
        m[0] = i;
        this.i = i;
        li.add(i);
    }

    public synchronized void setNull() {
        messages[0] = null; // after set null no new read should be reported
    }

    public synchronized void addMessage(int i) {
        q.add(i);
        a.add(i);
        b.add(i);
    }


    public synchronized String getMessageArr() {
        return "" + li.get(0) + messages[0] + " " + this.i;
    }

    public synchronized String getMessage() {
        return "" + a.get(0) + q.poll() + b.poll() + messages[0] + q.poll();
    }

    public static void main(String[] args) {
        try {
            Test tt = new Test();
            Client c = new Client();
            Thread t = new Thread(() -> {
                for (int i = 0; i < 1; i++) {
                    c.addMessageArr(42);
                    // double write should not be a new write
                }
                // c.setNull(); //reads from null should not appear as detected case
            });
            t.start();
            t.join();

            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 1; i++) {
                    // reading from the same position written by the same writer from the same path should not detected twice
                    System.out.println("" + c.getMessageArr());
                }
            });
            t2.start();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}