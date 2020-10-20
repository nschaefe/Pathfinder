package boundarydetection.tracker.util.logging;

import boundarydetection.tracker.util.LinkedList;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


public class HeavyBufferFileLoggerEngine extends LoggerEngine {

    // Use a simple data structure which we can copy into the code base to prevent instrumentation for that class
    private LinkedList<String[]> blockBuffer;
    private String[] block;
    private int cursor;

    private Thread t;

    private Object mutex = new Object();

    private static final int BLOCK_SIZE = 512;

    //4000 avg bytes per JSON entry
    public HeavyBufferFileLoggerEngine(int fileBufferSize, String fileName) {
        blockBuffer = new LinkedList<>();
        block = new String[BLOCK_SIZE];
        cursor = 0;


        t = new Thread(() -> {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter("./" + fileName), fileBufferSize);

                while (!blockBuffer.isEmpty() || !Thread.interrupted()) {
                    consumeWriteBlock(blockBuffer, out);
                }

                synchronized (mutex) {
                     writeBlock(block, cursor, out);
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

    private void consumeWriteBlock(LinkedList<String[]> buffer, BufferedWriter out) throws IOException {
        String[] entryBuffer;
        synchronized (mutex) {
            entryBuffer = buffer.poll();
        }
        if (entryBuffer == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ei) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        writeBlock(entryBuffer, entryBuffer.length, out);
    }

    private void writeBlock(String[] buffer, int end, BufferedWriter out) throws IOException {
        for (int i = 0; i < end; i++) {
            String entry = buffer[i];
            out.append(entry);
            out.newLine();
        }
    }

    @Override
    public void log(String mess) {
        synchronized (mutex) {
            if (cursor >= block.length) {
                blockBuffer.add(block);
                block = new String[BLOCK_SIZE];
                cursor = 0;
            }

            block[cursor] = mess;
            cursor++;
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
