package boundarydetection.tracker.util.logging;

public abstract class LoggerEngine {

    abstract void log(String mess);

    abstract void log(String mess, String tag);

    public void shutdown() throws InterruptedException {

    }

}
