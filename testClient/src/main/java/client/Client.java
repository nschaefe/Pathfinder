package client;

import java.util.ArrayDeque;
import java.util.ArrayList;

public class Client {

    private int[] messages = new int[2];
    private ArrayDeque<Integer> q = new ArrayDeque<>();
    private ArrayList<Integer> a = new ArrayList<>();

    public synchronized void addMessage(int i) {
        int[] m = messages;
        m[0] = i;
        q.add(i);
        a.add(i);
    }

    public synchronized int getMessage() {
        return a.get(0) + q.poll() + messages[0];
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