package client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

public class Client {

    private Object[] messages = new Object[2];
    private ArrayDeque<Integer> q = new ArrayDeque<>();

    public synchronized void addMessage(int i) {
        Object[] m = messages;
        m[0] = i;
        q.add(i);
    }

    public synchronized int getMessage() {
        return q.poll();
        //return (Integer) messages[0];
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
