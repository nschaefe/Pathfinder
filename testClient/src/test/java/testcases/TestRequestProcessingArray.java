package testcases;


import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestRequestProcessingArray extends TestRequestProcessingBase {

    @Override
    ExecutorService getExecutorService() {
        return new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
    }


}
