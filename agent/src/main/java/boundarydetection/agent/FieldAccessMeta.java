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
