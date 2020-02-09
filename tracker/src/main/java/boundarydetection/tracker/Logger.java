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
        Logger.path = path;
    }

    private static String path;
    private LoggingProvider tinylog;

    private Logger() {
        // REMARK: DO NOT use tinylog through the normal interface (Logger.debug etc). Because of late binding, the lib is "manually" loaded
        // at runtime. Since the tracker and tinylog is accessed via datastructures that are used while class loading,
        // this leads to a cyclic class loading error.
        // So we prevent this loading via a direct dependency of what should be loaded, what is the provider.
        Configuration.set("writer.file", path);
        Configuration.set("writer", "file");
        Configuration.set("writer.append", "true");
        Configuration.set("writer.buffered", "true");

        // REMARK: writer format with methodname captures stacktrace
        Configuration.set("writer.format", "{date} {level}" + System.lineSeparator() + "{message}");
        Configuration.set("writingthread", "true");
        Configuration.set("autoshutdown", "false");

        tinylog = new TinylogLoggingProvider();
    }

    public void log(String mess) {
        tinylog.log(2, null, Level.DEBUG, null, mess, null);
    }

    public void shutdown() throws InterruptedException {
        tinylog.shutdown();
    }

}
