package boundarydetection.tracker;

import java.util.Arrays;

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


    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FieldWriter)) return false;
        FieldWriter w = (FieldWriter) obj;
        return Arrays.equals(w.getStackTrace(),getStackTrace()) && w.getId() == getId();
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + Arrays.hashCode(getStackTrace());
        hash = hash * 31 + (int) id;
        return hash;
    }
}
