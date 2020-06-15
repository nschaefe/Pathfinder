package testcases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class TestDetectionCoverage extends TestBase {

    @Test
    public void testAllBasicTypesFieldAccess() throws InterruptedException {
        BasicGlobals r = new BasicGlobals();
        r.a = 1;
        r.b = 1;
        r.c = 1;
        r.d = true;
        r.e = 1;
        r.f = 1;
        r.g = 1;
        r.h = 1;

        Thread n = new Thread(r);
        n.start();
        n.join();
    }

    @Test
    public void testAllArraysFieldAccess() throws InterruptedException {
        ArrayGlobals r = createArrayGlobal();
        Thread n = new Thread(r);
        n.start();
        n.join();
    }

    @Test
    public void testAllArraysIndexAccess() throws InterruptedException {
        ArrayGlobals r = createArrayGlobal();
        r.a[0] = 1;
        r.b[0] = 1;
        r.c[0] = 1;
        r.d[0] = true;
        r.e[0] = 1;
        r.f[0] = 1;
        r.g[0] = 1;
        r.h[0] = 1;
        r.i[0] = 1;

        Thread n = new Thread(r);
        n.start();
        n.join();
    }


    private ArrayGlobals createArrayGlobal() {
        ArrayGlobals r = new ArrayGlobals();
        r.a = new int[1];
        r.b = new char[1];
        r.c = new short[1];
        r.d = new boolean[1];
        r.e = new long[1];
        r.f = new double[1];
        r.g = new byte[1];
        r.h = new float[1];
        r.i = new Object[1];
        return r;
    }

    private class ArrayGlobals implements Runnable {

        public int[] a;
        public char[] b;
        public short[] c;
        public boolean[] d;
        public long[] e;
        public double[] f;
        public byte[] g;
        public float[] h;
        public Object[] i;


        @Override
        public void run() {
            System.out.println("" + a[0] + b[0] + c[0] + d[0] + e[0] + f[0] + g[0] + h[0] + i[0]);
        }

    }

    private class BasicGlobals implements Runnable {

        public int a;
        public char b;
        public short c;
        public boolean d;
        public long e;
        public double f;
        public byte g;
        public float h;

        @Override
        public void run() {
            System.out.println("" + a + b + c + d + e + f + g + h);
        }
    }
}
