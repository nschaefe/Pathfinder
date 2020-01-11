package boundarydetection.agent;

import java.util.ArrayList;
import java.util.List;

public class FieldAccessMeta {

    private List<Long> reader;
    private List<Writer> writer;

    FieldAccessMeta() {
        this.reader = new ArrayList<>();
        this.writer = new ArrayList<>();
    }

    public void registerWriter() {
        // Only stacktrace matters because I care if the same thread comes from different traces.
        // This can be a different tasks executed by the same thread pool.
        // If the Id is different it could still be another task executed by the same thread from a thread pool
        // So id does not give a distinction. Execution path is what matters.
        long id = Thread.currentThread().getId();
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        writer.add(new Writer(id, trace));
    }

    public Writer otherWriterSingle() {
        List<Writer> l = otherWriter();
        if (l.isEmpty()) return null;
        return l.get(l.size() - 1);
    }

    public List<Writer> otherWriter() {
        long id = Thread.currentThread().getId();
        List<Writer> list = new ArrayList<>();
        for (Writer w : writer) {
            if (w.getId() != id) {
                list.add(w);
            }
        }
        return list;
    }


}
