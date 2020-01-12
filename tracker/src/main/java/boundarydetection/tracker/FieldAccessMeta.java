package  boundarydetection.tracker;

import java.util.LinkedList;
import java.util.List;

public class FieldAccessMeta {
    // WARNING HACKY
    // I use here LinkedList because it is currently not instrumented, using an instrumented class leads to recursion:
    // e.g. arraylist.add -> tracker ->arralist.add ->tracker....
    // this is a hack. We need a custom/renamed version that is not instrumented here

    private List<Long> reader;
    private List<FieldWriter> writer;

    FieldAccessMeta() {
        this.reader = new LinkedList<>();
        this.writer = new LinkedList<>();
    }

    public void registerWriter() {
        long id = Thread.currentThread().getId();
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        writer.add(new FieldWriter(id, trace));
    }

    public FieldWriter otherWriterSingle() {
        List<FieldWriter> l = otherWriter();
        if (l.isEmpty()) return null;
        return l.get(l.size() - 1);
    }

    public List<FieldWriter> otherWriter() {
        long id = Thread.currentThread().getId();
        List<FieldWriter> list = new LinkedList<>();
        for (FieldWriter w : writer) {
            if (w.getId() != id) {
                list.add(w);
            }
        }
        return list;
    }


}
