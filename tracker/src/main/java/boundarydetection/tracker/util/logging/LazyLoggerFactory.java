package boundarydetection.tracker.util.logging;

import java.util.function.Supplier;

public class LazyLoggerFactory extends LoggerEngine {

    private LoggerEngine engine;
    private Supplier<LoggerEngine> create;

    /*
     * Delays the creation of the logger until the first logging call is made.
     * The purpose of this concept is to avoid classloader deadlocks and recursive dependencies while class loading
     */
    public LazyLoggerFactory(Supplier<LoggerEngine> create) {
        this.create = create;
    }

    private void init() {
        if (engine == null)
            engine = create.get();
    }

    @Override
    public void log(String mess) {
        init();
        engine.log(mess);
    }

    @Override
    public void log(String mess, String tag) {
        init();
        engine.log(mess, tag);
    }

    @Override
    public void shutdown() throws InterruptedException {
        if (engine != null) engine.shutdown();
    }
}
