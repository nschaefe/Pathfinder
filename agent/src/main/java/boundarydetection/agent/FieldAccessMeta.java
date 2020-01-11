package boundarydetection.agent;

import java.util.ArrayList;
import java.util.List;

public class FieldAccessMeta {

    private List<Long> reader;
    private List<Long> writer;

    FieldAccessMeta() {
        this.reader = new ArrayList<>();
        this.writer = new ArrayList<>();
    }

    public void registerWriter() {
        long id = Thread.currentThread().getId();
        writer.add(id);
    }

    public boolean hasOtherWriter() {
        if (writer.isEmpty()) return false;

        long id = Thread.currentThread().getId();
        for (Long l : writer) if (l != id) return true;
        return false;
    }


}
