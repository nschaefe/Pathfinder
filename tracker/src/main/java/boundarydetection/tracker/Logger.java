package boundarydetection.tracker;

import org.tinylog.Level;
import org.tinylog.configuration.Configuration;
import org.tinylog.core.TinylogLoggingProvider;
import org.tinylog.provider.LoggingProvider;

public class Logger {

    private static Logger logger;

    public static Logger getLogger() {
        if (logger == null) {
            logger = new Logger();
        }
        return logger;
    }

    public static void configureLogger(String path) {

    }

    private LoggingProvider tinylog;

    private Logger() {
        Configuration.set("writer.file","./tracker_report.txt");
        Configuration.set("writer", "file");
        Configuration.set("writer.append", "true");
        Configuration.set("writer.buffered", "true");
        Configuration.set("writingthread", "true");
        tinylog = new TinylogLoggingProvider();
    }

    public void log(String mess) {
        tinylog.log(2, null, Level.DEBUG, null, mess, null);
    }

}
