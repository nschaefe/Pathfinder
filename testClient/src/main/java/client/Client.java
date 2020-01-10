package client;

public class Client {

    private int[] messages = new int[1];

    public synchronized void addMessage(int i) {
        messages[0] = i;
    }

    public synchronized int getMessage() {
        return messages[0];
    }

    public static void main(String[] args) {
        try {
            Client c = new Client();

            Thread t = new Thread(() -> c.addMessage(1));
            t.start();
            t.join();

            Thread t2 = new Thread(() -> c.getMessage());
            t2.start();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
