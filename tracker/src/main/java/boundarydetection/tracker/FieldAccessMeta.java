package boundarydetection.tracker;

import java.util.ArrayList;
import java.util.List;

public class FieldAccessMeta {
    // WARNING HACKY
    // I use here LinkedList because it is currently not instrumented, using an instrumented class leads to recursion:
    // e.g. arraylist.add -> tracker ->arralist.add ->tracker....
    // this is a hack. We need a custom/renamed version that is not instrumented here

    private List<Long> reader;
    private List<FieldWriter> writer;

    FieldAccessMeta() {
        //this.reader = new LinkedList<>();
        this.writer = new ArrayList<>();
    }

    // TODO Meta class should not contain decision logic
    private boolean check(StackTraceElement[] tr) {
        // currently a hack to get rid of these class loading reports
        for (int i = 0; i < tr.length; i++) {
            String cl = tr[i].getClassName();
            if (cl.startsWith("java.util.zip.ZipFile") ||
                    cl.startsWith("edu.brown.cs.systems")
            ) return false;
        }
        return true;
    }

    public void clearWriters() {
        writer.clear();
    }

    public void registerWriter() {
        long id = Thread.currentThread().getId();
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        if (!check(trace)) return;
        FieldWriter wr = new FieldWriter(id, trace);
        if (writer.indexOf(wr) < 0) writer.add(wr);
    }

    public FieldWriter otherWriterSingle() {
        List<FieldWriter> l = otherWriter();
        if (l.isEmpty()) return null;
        return l.get(l.size() - 1);
    }

    public List<FieldWriter> otherWriter() {
        long id = Thread.currentThread().getId();
        List<FieldWriter> list = new ArrayList<>();
        for (FieldWriter w : writer) {
            if (w.getId() != id) {
                list.add(w);
            }
        }
        return list;
    }


}
