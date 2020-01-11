package boundarydetection.agent;

import java.util.HashMap;
import java.util.List;

public class AccessTracker {


    public static int arrayReadInt(Object arr, int index) {
        //System.out.println("array read by: " + Thread.currentThread().getId());

        ArrayField f = new ArrayField(int[].class, arr, index);
        arrayRead(f);

        return ((int[]) arr)[index];
    }

    public static void arrayWriteInt(Object arr, int index, int value) {
        //System.out.println("array write by: " + Thread.currentThread().getId());

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
        arrayWrite(f);
        ((Object[]) arr)[index] = value;
    }

    public synchronized static void arrayWrite(ArrayField f) {
        System.out.println("WRITE: " + toString(Thread.currentThread().getStackTrace()));
        FieldAccessMeta meta = accesses.get(f);
        if (meta == null) {
            meta = new FieldAccessMeta();
            accesses.put(f, meta);
        }

        meta.registerWriter();
    }

    public synchronized static void arrayRead(ArrayField f) {
        System.out.println("READ: " + toString(Thread.currentThread().getStackTrace()));
        FieldAccessMeta meta = accesses.get(f);
        if (meta == null) return;

        // TODO log code location explicitly
        List<Writer> l = meta.otherWriter();
        StringBuilder s = new StringBuilder();
        for (Writer w : l) {
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
        System.out.println(s.toString());

    }

    private static String toString(StackTraceElement[] trace) {
        StringBuilder s = new StringBuilder();
        for (StackTraceElement el : trace) {
            s.append(el);
            s.append(System.lineSeparator());
        }
        return s.toString();
    }

    private static HashMap<IField, FieldAccessMeta> accesses = new HashMap<>();

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
