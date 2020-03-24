package testcases;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestRequestProcessingLinkedList extends  TestRequestProcessingBase{

    @Override
    ExecutorService getExecutorService() {
     return Executors.newFixedThreadPool(4);
    }
}
