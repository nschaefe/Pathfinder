package boundarydetection.tracker;

import java.util.ArrayList;
import java.util.List;

public class FieldAccessMeta {
    private List<FieldWriter> writer;
    private int writeCounter;
    private boolean matched;

    FieldAccessMeta() {
        matched = false;
        this.writeCounter = 0;
        this.writer = new ArrayList<>();
    }

    public void clearWriters() {
        writer.clear();
    }

    public void registerWriter() {
        long id = Thread.currentThread().getId();

        Throwable trace = new Throwable();
        FieldWriter wr = new FieldWriter(id, trace);
        if (writer.size() == 0) writer.add(wr);
        else writer.set(0, wr);
        writeCounter++;
    }

    public int getWriteCount() {
        return writeCounter;
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
        //TODO happens implicit, not so nice (rather register reader)
        if (!list.isEmpty()) matched = true;
        return list;
    }

    public boolean hasMatch() {
        return matched;
    }
}
