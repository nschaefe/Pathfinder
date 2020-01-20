package client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;

public class Client {

    private int[] messages = new int[2];
    private ArrayDeque<Integer> q = new ArrayDeque<>();
    private ArrayList<Integer> a = new ArrayList<>();
    private ArrayBlockingQueue<Integer> b = new ArrayBlockingQueue<Integer>(5);
//  private HashSet<Integer> h= new HashSet<>();

    public synchronized void addMessage(int i) {
        int[] m = messages;
        m[0] = i;
        q.add(i);
        a.add(i);
        b.add(i);
    }

    public synchronized String getMessage() {
        return ""+a.get(0) + q.poll() + b.poll() + messages[0]+q.poll();
    }

    public static void main(String[] args) {
        try {
            Client c = new Client();
            Thread t = new Thread(() -> c.addMessage(42));
            t.start();
            t.join();

            Thread t2 = new Thread(() -> System.out.println("" + c.getMessage()));
            t2.start();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}