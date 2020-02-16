package boundarydetection.tracker;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ReportGenerator {

    private static final JsonFactory factory = new JsonFactory();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss-MM.dd.yyyy");

    public static String generateDetectionReportJSON(long readerThreadID, StackTraceElement[] readerTrace,
                                                     AbstractFieldLocation loc, FieldWriter w, FieldAccessMeta meta) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ByteArrayOutputStream.close has no effect, no closing neccessary
        try {
            JsonGenerator g = factory.createGenerator(out);
            g.writeStartObject();
            g.writeStringField("time", getTime());
            g.writeStringField("type", "CONCURRENT WRITE/READ DETECTION");
            loc.toJSON(g);
            g.writeNumberField("reader_thread_id", readerThreadID);
            g.writeNumberField("writer_thread_id", w.getId());
            g.writeNumberField("writer_count", meta.getWriteCount());
            g.writeStringField("reader_stacktrace", Util.toString(readerTrace));
            g.writeStringField("writer_stacktrace", Util.toString(w.getStackTrace()));
            g.writeEndObject();
            g.close();
            return out.toString();
        } catch (IOException e) {
            System.err.println("BUG");
            e.printStackTrace();
            return "$$BUG";
        }
    }

    public static String generateSingleAccessedReportJSON(String[] singleWrite) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ByteArrayOutputStream.close has no effect, no closing neccessary
        try {
            JsonGenerator g = factory.createGenerator(out);
            g.writeStartObject();
            g.writeStringField("time", getTime());
            g.writeStringField("type", "GLOBALLY WRITTEN ONCE FIELDS");
            g.writeStringField("pid", Util.getProcessDesc());
            g.writeArrayFieldStart("fields");
            for (String s : singleWrite) g.writeString(s);
            g.writeEndArray();
            g.writeEndObject();
            g.close();
            return out.toString();
        } catch (IOException e) {
            System.err.println("BUG");
            e.printStackTrace();
            return "$$BUG";
        }
    }

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
        s.append("(" + w.getId() + ")");
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