package boundarydetection.tracker;

import java.util.HashMap;
import java.util.List;

public class AccessTracker {

    static {
        Logger.initLogger("./");
    }

    private static HashMap<IField, FieldAccessMeta> accesses = new HashMap<>();
    // A thread local is used to break the recursion. Internally used classes also access fields and arrays which leads to recursion.
    private static ThreadLocal<Boolean> insideTracker = new ThreadLocal<Boolean>();

    public synchronized static void arrayWrite(ArrayField f) {
        arrayWrite(f, false);
    }

    public synchronized static void arrayWrite(ArrayField f, boolean valueIsNull) {
        // To break the recursion
        if (insideTracker.get() != null) return;
        try {
            insideTracker.set(true);

            // System.out.println("WRITE: " + toString(Thread.currentThread().getStackTrace()));
            // TODO do not track if set value is null, so an entry is deleted
            if (valueIsNull) return;
            FieldAccessMeta meta = accesses.get(f);
            if (meta == null) {
                meta = new FieldAccessMeta();
                accesses.put(f, meta);
            }

            meta.registerWriter();
        } finally {
            insideTracker.remove();
        }

    }

    public synchronized static void arrayRead(ArrayField f) {
        // To break the recursion
        if (insideTracker.get() != null) return;
        try {
            insideTracker.set(true);
            // System.out.println("READ: " + toString(Thread.currentThread().getStackTrace()));
            FieldAccessMeta meta = accesses.get(f);
            if (meta == null) return;

            // TODO log code location explicitly
            List<FieldWriter> l = meta.otherWriter();
            if (l.isEmpty()) return;

            StringBuilder s = new StringBuilder();
            //for (FieldWriter w : l) {
            for (FieldWriter w: l) {
                s.append("CONCURRENT WRITE/READ DETECTED");
                s.append(System.lineSeparator());
                s.append("Reader");
                s.append("(" + Thread.currentThread().getId() + ")");
                s.append(" trace:");
                s.append(System.lineSeparator());
                s.append(toString(Thread.currentThread().getStackTrace()));
                s.append(System.lineSeparator());
                s.append("----------------");
                s.append(System.lineSeparator());
                s.append("Writer");
                s.append("(" + w.getId() + ")");
                s.append(" trace:");
                s.append(System.lineSeparator());
                s.append(toString(w.getStackTrace()));
            }
            Logger.getLogger().log(s.toString());
        } finally {
            insideTracker.remove();
        }
    }

    public static int arrayReadInt(Object arr, int index) {
        ArrayField f = new ArrayField(int[].class, arr, index);
        arrayRead(f);

        return ((int[]) arr)[index];
    }

    public static void arrayWriteInt(Object arr, int index, int value) {
        ArrayField f = new ArrayField(int[].class, arr, index);
        arrayWrite(f);

        ((int[]) arr)[index] = value;
    }

    public static Object arrayReadObject(Object arr, int index) {
        ArrayField f = new ArrayField(Object[].class, arr, index);
        arrayRead(f);
        return ((Object[]) arr)[index];
    }

    public static void arrayWriteObject(Object arr, int index, Object value) {
        ArrayField f = new ArrayField(Object[].class, arr, index);
        arrayWrite(f, value == null);
        ((Object[]) arr)[index] = value;
    }

    //TODO move to util
    private static String toString(StackTraceElement[] trace) {
        StringBuilder s = new StringBuilder();
        for (StackTraceElement el : trace) {
            s.append(el);
            s.append(System.lineSeparator());
        }
        return s.toString();
    }

    //  arrayReadByteOrBoolean
//  arrayWriteByteOrBoolean
//  arrayReadChar
//  arrayWriteChar
//  arrayReadDouble
//  arrayWriteDouble
//  arrayReadFloat
//  arrayWriteFloat
//  arrayReadInt
//  arrayWriteInt
//  arrayReadLong
//  arrayWriteLong
//  arrayReadShort
//  arrayWriteShort


}
