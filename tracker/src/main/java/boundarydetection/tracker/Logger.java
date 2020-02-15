package boundarydetection.tracker;

import org.tinylog.Level;
import org.tinylog.configuration.Configuration;
import org.tinylog.core.TinylogLoggingProvider;
import org.tinylog.provider.LoggingProvider;

import java.util.Random;

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
    private TinylogLoggingProvider tinylog;

    private Logger() {
        // REMARK: DO NOT use tinylog through the normal interface (Logger.debug etc). Because of late binding, the lib is "manually" loaded
        // at runtime. Since the tracker and tinylog is accessed via datastructures that are used while class loading,
        // this leads to a cyclic class loading error.
        // So we prevent this loading via a direct dependency of what should be loaded, what is the provider.
        int random = (new Random()).nextInt(Integer.MAX_VALUE);
        Configuration.set("writer.file", path + '_' + random);
        Configuration.set("writer", "file");
        Configuration.set("writer.append", "true");
        Configuration.set("writer.buffered", "true");
        Configuration.set("writer.tag", "DETECTION");
        Configuration.set("writer.format", "{message}");// REMARK: writer format with methodname captures stacktrace

        Configuration.set("writer2.file", path + "GLOBALS_" + random);
        Configuration.set("writer2", "file");
        Configuration.set("writer2.append", "true");
        Configuration.set("writer2.buffered", "true");
        Configuration.set("writer2.tag", "GLOBALS");
        Configuration.set("writer2.format", "{message}");

        Configuration.set("writingthread", "true");
        Configuration.set("autoshutdown", "false");

        tinylog = new TinylogLoggingProvider();
    }

    public void log(String mess) {
        log(mess, null);
    }

    public void log(String mess, String tag) {
        tinylog.log(2, tag, Level.DEBUG, null, mess, null);
    }

    public void shutdown() throws InterruptedException {
        tinylog.shutdown();
    }

}
