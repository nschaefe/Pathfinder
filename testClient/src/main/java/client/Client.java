package client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;

public class Client {

    private Integer[] messages = new Integer[2];
    private ArrayDeque<Integer> q = new ArrayDeque<>();
    private ArrayList<Integer> a = new ArrayList<>();
    private ArrayBlockingQueue<Integer> b = new ArrayBlockingQueue<Integer>(5);
//  private HashSet<Integer> h= new HashSet<>();

    public synchronized void addMessageArr(int i) {
        Integer[] m = messages;
        m[0] = i;
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
        return "" + messages[0];
    }

    public synchronized String getMessage() {
        return "" + a.get(0) + q.poll() + b.poll() + messages[0] + q.poll();
    }

    public static void main(String[] args) {
        try {
            Client c = new Client();
            Thread t = new Thread(() -> {
                for (int i = 0; i < 2; i++) {
                    c.addMessageArr(42);
                    // double write should not be a new write
                }
                c.setNull(); //reads from null should not appear as detected case
            });
            t.start();
            t.join();

            Thread t2 = new Thread(() -> System.out.println("" + c.getMessageArr()));
            t2.start();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}