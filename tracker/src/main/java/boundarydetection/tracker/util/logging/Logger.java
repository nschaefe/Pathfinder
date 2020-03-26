package boundarydetection.tracker.util.logging;

public class Logger {

    //STATIC FACADE---------------
    private volatile static LoggerEngine logger;

    public static void setLoggerIfNo(LoggerEngine log) {
        if (logger != null) return;
        setLogger(log);
    }

    public static void setLogger(LoggerEngine log) {
        if (logger != null) {
            try {
                logger.shutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        logger = log;
    }

    public static LoggerEngine getLogger() {
        return logger;
    }

    public static void log(String s) {
        logger.log(s);
    }

    public static void log(String mess, String tag) {
        logger.log(mess, tag);
    }


    public static void shutdown() throws InterruptedException {
        logger.shutdown();
    }


}
