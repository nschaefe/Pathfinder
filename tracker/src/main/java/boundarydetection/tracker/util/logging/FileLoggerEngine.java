package boundarydetection.tracker.util.logging;

import org.tinylog.Level;
import org.tinylog.configuration.Configuration;
import org.tinylog.core.TinylogLoggingProvider;

public class FileLoggerEngine extends LoggerEngine {

    private TinylogLoggingProvider tinylog;

    public FileLoggerEngine(String path) {
        // REMARK: DO NOT use tinylog through the normal interface (Logger.debug etc). Because of late binding, the lib is "manually" loaded
        // at runtime. Since the tracker and tinylog is accessed via datastructures that are used while class loading,
        // this leads to a cyclic class loading error.
        // So we prevent this loading via a direct dependency of what should be loaded, what is the provider.
        Configuration.set("writer.file", path);
        Configuration.set("writer", "file");
        Configuration.set("writer.append", "true");
        Configuration.set("writer.buffered", "true");
        Configuration.set("writer.format", "{message}");// REMARK: writer format with methodname captures stacktrace
        Configuration.set("writingthread", "true");
        tinylog = new TinylogLoggingProvider();
    }

    @Override
    public void log(String mess) {
        log(mess, null);
    }

    @Override
    public void log(String mess, String tag) {
        tinylog.log(2, tag, Level.DEBUG, null, mess, null);
    }

    @Override
    public void shutdown() throws InterruptedException {
        tinylog.shutdown();
    }

}
