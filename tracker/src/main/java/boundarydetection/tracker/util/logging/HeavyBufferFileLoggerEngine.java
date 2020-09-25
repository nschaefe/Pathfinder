package boundarydetection.tracker.util.logging;

import boundarydetection.tracker.util.ArrayBlockingQueue;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
                BufferedWriter out = new BufferedWriter(new FileWriter("./" + fileName + ".json"), (bufferSize / 2) * 4000);

                while (!buffer.isEmpty() || !Thread.interrupted()) {
                    writeBatch(buffer, out);
                }
                out.append("DONE");
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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
            e.printStackTrace();
        }
    }

    @Override
    public void log(String mess, String tag) {
        throw new NotImplementedException();
    }

    public void shutdown() throws InterruptedException {
        t.interrupt();
        t.join();
    }
}
