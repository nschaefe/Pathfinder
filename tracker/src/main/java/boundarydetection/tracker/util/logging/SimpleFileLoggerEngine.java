package boundarydetection.tracker.util.logging;

import boundarydetection.tracker.util.ArrayBlockingQueue;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class SimpleFileLoggerEngine extends LoggerEngine {

    private BufferedWriter out;

    //4000 avg bytes per JSON entry
    public SimpleFileLoggerEngine(int bufferSize, String fileName){
        try {
            out = new BufferedWriter(new FileWriter("./" + fileName + ".json"), bufferSize * 4000);
        } catch (IOException e) {
            // HEAVY BUG
            e.printStackTrace();
        }
    }

    @Override
    public void log(String mess) {
        try {
            out.append(mess);
            out.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void log(String mess, String tag) {
        throw new NotImplementedException();
    }

    public void shutdown() throws InterruptedException {
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
