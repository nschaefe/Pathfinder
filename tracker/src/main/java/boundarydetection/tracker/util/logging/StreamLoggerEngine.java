package boundarydetection.tracker.util.logging;

import java.io.IOException;
import java.io.OutputStream;

public class StreamLoggerEngine extends LoggerEngine {

    private OutputStream stream;

    public StreamLoggerEngine(OutputStream stream) {
        this.stream = stream;
    }

    public void log(String mess) {
        try {
            stream.write((mess + System.lineSeparator()).getBytes());
        } catch (IOException e) {
            System.out.println("Could not write to stream: " + mess);
        }
    }

    public void log(String mess, String tag) {
        throw new UnsupportedOperationException();
    }


}
