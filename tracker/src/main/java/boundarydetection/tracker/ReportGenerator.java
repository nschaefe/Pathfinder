package boundarydetection.tracker;

import boundarydetection.tracker.tasks.Task;
import boundarydetection.tracker.tasks.Tasks;
import boundarydetection.tracker.util.Util;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ReportGenerator {

    private static final JsonFactory factory = new JsonFactory();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss-MM.dd.yyyy");
    private static final int STACKTRACE_MAX_DEPTH = 55;
    private static final String CLASS_PREFIX = "boundarydetection";


    public static String generateDetectionReportJSON(int epoch, long readerThreadID, StackTraceElement[] readerTrace,
                                                     AbstractFieldLocation loc, FieldWriter w, FieldAccessMeta meta, String id) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ByteArrayOutputStream.close has no effect, no closing neccessary
        try {
            JsonGenerator g = factory.createGenerator(out);
            g.writeStartObject();
            g.writeNumberField("epoch", epoch);
            g.writeStringField("time", getTime());
            g.writeStringField("tag", "CONCURRENT WRITE/READ DETECTION");
            loc.toJSON(g);
            g.writeNumberField("reader_thread_id", readerThreadID);
            g.writeNumberField("writer_thread_id", w.getThreadID());
            g.writeStringField("writer_taskID", w.getTask().getTaskID());
            g.writeNumberField("writer_count", meta.getWriteCount());
            g.writeNumberField("writer_th_clock", w.getClock());
            g.writeStringField("eventID", id);
            g.writeStringField("reader_stacktrace", Util.toString(readerTrace, CLASS_PREFIX, STACKTRACE_MAX_DEPTH));
            g.writeStringField("writer_stacktrace", Util.toString(w.getStackTrace(), CLASS_PREFIX, STACKTRACE_MAX_DEPTH));

            g.writeEndObject();
            g.close();
            return out.toString();
        } catch (IOException e) {
            System.err.println("BUG");
            e.printStackTrace();
            return "$$BUG";
        }
    }

    public static String generateMessageJSON(String message, String tag, int epoch) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ByteArrayOutputStream.close has no effect, no closing neccessary
        try {
            JsonGenerator g = factory.createGenerator(out);
            g.writeStartObject();
            g.writeStringField("time", getTime());
            g.writeStringField("tag", tag);
            g.writeStringField("text", message);
            g.writeNumberField("epoch", epoch);
            g.writeNumberField("thread_id", Thread.currentThread().getId());

            if (Tasks.hasTask()) {
                Task t = Tasks.getTask();
                g.writeStringField("taskID", t.getTaskID());
                g.writeNumberField("inheritanceCount", t.getInheritanceCount());
                g.writeArrayFieldStart("parentEventID");
                for (String e : t.getParentEventIDs()) {
                    g.writeString(e);
                }
                g.writeEndArray();
                g.writeStringField("eventID", t.getEventID());
            }
            g.writeEndObject();
            g.close();
            return out.toString();
        } catch (IOException e) {
            System.err.println("BUG");
            e.printStackTrace();
            return "$$BUG";
        }
    }


    @Deprecated
    public String generateDetectionReportSimple(long readerThreadID, StackTraceElement[] readerTrace, AbstractFieldLocation loc, FieldWriter w) {
        StringBuilder s = new StringBuilder();
        s.append("$$CONCURRENT WRITE/READ DETECTED");
        s.append(" at " + loc.getLocation());
        s.append(System.lineSeparator());
        s.append("Reader");
        s.append("(" + readerThreadID + ")");
        s.append(" trace:");
        s.append(System.lineSeparator());
        s.append(Util.toString(readerTrace));
        s.append(System.lineSeparator());
        s.append("----------------");
        s.append(System.lineSeparator());
        s.append("Writer");
        s.append("(" + w.getThreadID() + ")");
        s.append(" trace:");
        s.append(System.lineSeparator());
        s.append(Util.toString(w.getStackTrace()));
        s.append(System.lineSeparator());
        return s.toString();
    }

    private static String getTime() {
        return ZonedDateTime.now().format(formatter);
    }
}
