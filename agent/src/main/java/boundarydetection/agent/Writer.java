package boundarydetection.agent;

public class Writer {

    private long id;
    private StackTraceElement[] trace;

    public Writer(long id, StackTraceElement[] trace) {
        this.id = id;
        this.trace = trace;
    }

    public long getId() {
        return id;
    }

    public StackTraceElement[] getStackTrace() {
        return trace;
    }

}
