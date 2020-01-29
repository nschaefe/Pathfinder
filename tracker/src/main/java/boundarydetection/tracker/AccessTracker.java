package boundarydetection.tracker;

import java.util.HashMap;
import java.util.List;

public class AccessTracker {

    static {
        Logger.initLogger("./");
    }


    //TODO only track the last direct writer to a field (not for move, arraycopy), this simplifies most of the other TODOs, reasoning on record

    //TODO remove metadata of accesses to objects that died
    // do this with weakreferences + periodic garbage collection

    //TODO mark fieldwriter with readers, if we already saw this reader reading from the same writer case, do not report again (currently solved by distinct.py)
    //TODO refactor, keep complexity of redundancy recognition outside of basic recognition
    //TODO reading and writing an object to another array index or another array (copied), does
    // not loose old writers, if we associate writers with an object.
    // easy way for refactoring-> We currently look at accesses to ArrayFields or Fields (represent locations) and track accesses to them
    // we just have to redefine what an access location is. Instead of fields and arrays we say objects if we have
    // an object at hand or the field location (array index, field) if it is a primitive.

    //TODO when do we remove writers from the list?
    // Problem: when a thread initiates an object and spawns a thread that works over a queue inside that object
    // the worker reads the field for every access. This gives a write/read relation for every access between the
    // thread that initiated that class and the thread that works over it.
    // this is actually a false positive.
    // if the writer disappears at some point we could reduce the amount of false positives.




    private static HashMap<IField, FieldAccessMeta> accesses = new HashMap<>();
    // A thread local is used to break the recursion. Internally used classes also access fields and arrays which leads to recursion.
    private static ThreadLocal<Boolean> insideTracker = new ThreadLocal<Boolean>();

    public synchronized static void writeAccess(IField f) {
        writeAccess(f, false);
    }

    public synchronized static void writeAccess(IField f, boolean valueIsNull) {
        // To break the recursion
        if (insideTracker.get() != null) return;
        try {
            insideTracker.set(true);
            FieldAccessMeta meta = accesses.get(f);
            if (meta == null) {
                meta = new FieldAccessMeta();
                accesses.put(f, meta);
            }
            if (valueIsNull) meta.clearWriters();
            else meta.registerWriter();
        } finally {
            insideTracker.remove();
        }

    }

    public synchronized static void readAccess(IField f) {
        // To break the recursion
        if (insideTracker.get() != null) return;
        try {
            insideTracker.set(true);
            // System.out.println("READ: " + toString(Thread.currentThread().getStackTrace()));
            FieldAccessMeta meta = accesses.get(f);
            if (meta == null) return;

            List<FieldWriter> l = meta.otherWriter();
            if (l.isEmpty()) return;

            StringBuilder s = new StringBuilder();
            for (FieldWriter w : l) {
                s.append("$$CONCURRENT WRITE/READ DETECTED");
                s.append(System.lineSeparator());
                s.append("Reader");
                s.append("(" + Thread.currentThread().getId() + ")");
                s.append(" trace:");
                s.append(System.lineSeparator());
                s.append(Util.toString(Thread.currentThread().getStackTrace()));
                s.append(System.lineSeparator());
                s.append("----------------");
                s.append(System.lineSeparator());
                s.append("Writer");
                s.append("(" + w.getId() + ")");
                s.append(" trace:");
                s.append(System.lineSeparator());
                s.append(Util.toString(w.getStackTrace()));
                s.append(System.lineSeparator());
            }
            Logger.getLogger().log(s.toString());
        } finally {
            insideTracker.remove();
        }
    }

    public static int arrayReadInt(Object arr, int index) {
        ArrayField f = new ArrayField(int[].class, arr, index);
        readAccess(f);

        return ((int[]) arr)[index];
    }

    public static void arrayWriteInt(Object arr, int index, int value) {
        ArrayField f = new ArrayField(int[].class, arr, index);
        writeAccess(f);

        ((int[]) arr)[index] = value;
    }

    public static Object arrayReadObject(Object arr, int index) {
        ArrayField f = new ArrayField(Object[].class, arr, index);
        readAccess(f);
        return ((Object[]) arr)[index];
    }

    public static void arrayWriteObject(Object arr, int index, Object value) {
        ArrayField f = new ArrayField(Object[].class, arr, index);
        writeAccess(f, value == null);
        ((Object[]) arr)[index] = value;
    }


    public static void readObject(Object value, Object parent, String location) {
        Field f = new Field(location, Object.class, parent);
        readAccess(f);
    }

    public static void writeObject(Object value, Object parent,  String location) {
        Field f = new Field(location, Object.class, parent);
        writeAccess(f, value == null);
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
