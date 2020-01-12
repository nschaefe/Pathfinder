package boundarydetection.tracker;

public class FieldWriter {

    private long id;
    private StackTraceElement[] trace;

    public FieldWriter(long id, StackTraceElement[] trace) {
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
