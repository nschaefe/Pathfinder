package boundarydetection.tracker.util.logging;

public abstract class LoggerEngine {

    public abstract void log(String mess);

    public abstract void log(String mess, String tag);

    public void shutdown() throws InterruptedException {

    }

}
