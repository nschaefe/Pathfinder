package boundarydetection.tracker.util.logging;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.*;

public class HeavyBufferFileLoggerEngine extends LoggerEngine {

    private ConcurrentLinkedQueue<String[]> blockBuffer;
    private String[] block;
    private int cursor;
    private Thread t;

    private static final int BLOCK_SIZE = 512;

    //4000 avg bytes per JSON entry
    public HeavyBufferFileLoggerEngine(int fileBufferSize, String fileName) {
        blockBuffer = new ConcurrentLinkedQueue<String[]>();
        block = new String[BLOCK_SIZE];
        cursor = 0;


        t = new Thread(() -> {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter("./" + fileName), fileBufferSize);

                while (!blockBuffer.isEmpty() || !Thread.interrupted()) {
                    writeBlock(blockBuffer, out);
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

    private void writeBlock(ConcurrentLinkedQueue<String[]> buffer, BufferedWriter out) throws IOException {
        String[] entryBuffer = buffer.poll();
        if (entryBuffer == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ei) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        for (String entry : entryBuffer) {
            out.append(entry);
            out.newLine();
        }
        //out.flush();
    }

    @Override
    public void log(String mess) {
        if (cursor >= block.length) {
            blockBuffer.add(block);
            block = new String[BLOCK_SIZE];
            cursor = 0;
        }

        block[cursor] = mess;
        cursor++;
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
