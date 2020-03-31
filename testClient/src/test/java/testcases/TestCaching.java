package testcases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Random;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCaching extends TestBase {

    private HashMap<Integer, Object> cache;

    public TestCaching() {
        cache = new HashMap<>();
    }

    @Test
    public void concurrentCache() throws InterruptedException {
        Util.asyncExecute(5, () -> new workerRunnable());
    }

    private synchronized Object getObject(int id) {
        Object o = cache.get(id);
        if (o == null) {
            o = fetchObject(id);
            cache.put(id, o);
        }
        return o;
    }

    private Object fetchObject(int id) {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("fetched object: " + id);
        return id;
    }

    class workerRunnable implements Runnable {
        @Override
        public void run() {
            Random rand = new Random();
            int id = rand.nextInt(2);
            Object o = getObject(id);
            System.out.println("got object: " + id);
        }
    }


}
