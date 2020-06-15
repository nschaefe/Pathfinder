package testcases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class TestArrayNameInference extends TestBase {

    @Test
    public void testNameInferenceRead() throws InterruptedException {
        R r = new R();
        r.a = new String[1];
        r.a[0] = "";
        Thread n = new Thread(r);
        n.start();
        n.join();
    }

    @Test
    public void testNameInferenceWrite() throws InterruptedException {
        String[] aa = new String[1];
        aa[0] = "1";
        Thread n = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println(aa[0]);
            }
        });
        n.start();
        n.join();
    }

    private class R implements Runnable {

        public String[] a;

        @Override
        public void run() {
            System.out.println(a[0]);
        }
    }
}
