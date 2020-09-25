package testcases;

import boundarydetection.tracker.util.logging.HeavyBufferFileLoggerEngine;
import boundarydetection.tracker.util.logging.LoggerEngine;
import boundarydetection.tracker.util.logging.SimpleFileLoggerEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestLogging {

    private static final int bufferSize = 50000;
    private static final int writeCount = 200000;

    @Test
    public void testHeavyBufferFileLoggerEngine() throws InterruptedException {
        LoggerEngine logger = new HeavyBufferFileLoggerEngine(bufferSize, "loggerTest");
        String pre = "{\"serial\":0,\"tag\":\"CONCURRENT WRITE/READ DETECTION\",\"writer_task_tag\":null," +
                "\"location\":\"core.Main$$Lambda$3.arg$1\",\"field_object_type\":\"class java.lang.Object\"," +
                "\"parent\":933646237,\"traceID\":\"e3c41f0b-5c90-4dd8-9383-6abb698e1271\"," +
                "\"sub_traceID\":\"e3c41f0b-5c90-4dd8-9383-6abb698e1271_0.2953590465343209\"," +
                "\"global_task_serial\":1,\"global_writer_serial\":44,\"writer_thread_id\":1," +
                "\"reader_thread_id\":8,\"reader_joined_trace_ids\":[]," +
                "\"reader_stacktrace\":[\"java.lang.Thread.run(Thread.java:748)\"]," +
                "\"writer_stacktrace\":[\"core.Main$$Lambda$3/45643137.<init>(Unknown Source)\"," +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "]}";

        pre = pre + pre + pre;
        System.out.println(pre.length());

        long startTime = System.nanoTime();
        for (int i = 0; i < writeCount; i++) {
            logger.log(pre + i);
        }
        long endTime = System.nanoTime();

        logger.shutdown();

        long timeElapsed = endTime - startTime;
        System.out.println("Execution time in milliseconds : " +
                timeElapsed / 1000000);
    }

    @Test
    public void testSimpleLogger() throws InterruptedException, IOException {
        LoggerEngine logger = new SimpleFileLoggerEngine(bufferSize, "loggerTest");
        String pre = "{\"serial\":0,\"tag\":\"CONCURRENT WRITE/READ DETECTION\",\"writer_task_tag\":null," +
                "\"location\":\"core.Main$$Lambda$3.arg$1\",\"field_object_type\":\"class java.lang.Object\"," +
                "\"parent\":933646237,\"traceID\":\"e3c41f0b-5c90-4dd8-9383-6abb698e1271\"," +
                "\"sub_traceID\":\"e3c41f0b-5c90-4dd8-9383-6abb698e1271_0.2953590465343209\"," +
                "\"global_task_serial\":1,\"global_writer_serial\":44,\"writer_thread_id\":1," +
                "\"reader_thread_id\":8,\"reader_joined_trace_ids\":[]," +
                "\"reader_stacktrace\":[\"java.lang.Thread.run(Thread.java:748)\"]," +
                "\"writer_stacktrace\":[\"core.Main$$Lambda$3/45643137.<init>(Unknown Source)\"," +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "\"core.Main$$Lambda$3/45643137.get$Lambda(Unknown Source)\",\"core.Main.main(Main.java:13)\"" +
                "]}";

        pre = pre + pre + pre;
        System.out.println(pre.length());

        long startTime = System.nanoTime();
        for (int i = 0; i < writeCount; i++) {
            logger.log(pre + i);
        }
        long endTime = System.nanoTime();

        logger.shutdown();

        long timeElapsed = endTime - startTime;
        System.out.println("Execution time in milliseconds : " +
                timeElapsed / 1000000);
    }
}
