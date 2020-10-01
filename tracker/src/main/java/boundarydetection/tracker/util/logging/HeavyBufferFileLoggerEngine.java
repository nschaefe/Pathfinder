package boundarydetection.tracker.util.logging;

import boundarydetection.tracker.util.ArrayBlockingQueue;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.*;

public class HeavyBufferFileLoggerEngine extends LoggerEngine {

    private ArrayBlockingQueue<String> buffer;
    private Collection<String> pullBuffer;
    private Thread t;

    //4000 avg bytes per JSON entry
    public HeavyBufferFileLoggerEngine(int bufferSize, String fileName) {
        buffer = new ArrayBlockingQueue<String>(bufferSize);
        pullBuffer = new ArrayList<>(bufferSize);

        t = new Thread(() -> {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter("./" + fileName), (bufferSize / 2) * 4000);

                while (!buffer.isEmpty() || !Thread.interrupted()) {
                    writeBatch(buffer, out);
                }
                out.flush();
                out.close();
                System.out.println("LoggerThreadFinished");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();

    }

    private void writeSingle(BlockingQueue<String> buffer, BufferedWriter out) throws IOException {
        try {
            String entry = buffer.take();
            out.append(entry);
            out.newLine();
        } catch (InterruptedException ei) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeBatch(BlockingQueue buffer, BufferedWriter out) throws IOException {
        pullBuffer.clear();
        buffer.drainTo(pullBuffer);

        for (String entry : pullBuffer) {
            out.append(entry);
            out.newLine();
        }

        out.flush();

        try {
            Thread.sleep(1);
        } catch (InterruptedException ei) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void log(String mess) {
        try {
            buffer.put(mess);
        } catch (InterruptedException e) {
            // Interrupt comes from outside (application code) we have to continue the interrupt effect back to the caller
            // we retry once again to not loose data
            try {
                buffer.put(mess);
            } catch (InterruptedException ex) {
                System.err.println("thread tried to log but got interrupted:\n" + mess);
            }
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void log(String mess, String tag) {
        log(tag + ": " + mess);
    }

    public void shutdown() throws InterruptedException {
        t.interrupt();
        t.join();
    }
}
