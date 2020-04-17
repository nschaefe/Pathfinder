package boundarydetection.tracker;

import boundarydetection.tracker.tasks.Tasks;
import boundarydetection.tracker.util.logging.FileLoggerEngine;
import boundarydetection.tracker.util.logging.LazyLoggerFactory;
import boundarydetection.tracker.util.logging.Logger;
import boundarydetection.tracker.util.logging.StreamLoggerEngine;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class AccessTracker {

    private static final int MAP_INIT_SIZE = 10000;
    public static volatile int MAX_EVENT_COUNT = 55;

    // REMARK: recursion at runtime and while classloading can lead to complicated deadlocks (more on voice record)
    // is used to break the recursion. Internally used classes also access fields and arrays which leads to recursion.
    private static volatile InheritableThreadLocal<Boolean> insideTracker;
    private static boolean inited = false;
    private static Object initLock = new Object();

    private static HashMap<AbstractFieldLocation, FieldAccessMeta> accesses;
    private static int epoch = 0;
    private static volatile boolean enabled = false;

    private static void init() {
        synchronized (initLock) {
            if (!inited) {
                // DO NOT PUT inited AT THE END (NECESSARY FOR RECURSION BREAKING)
                inited = true;

                int random = (new Random()).nextInt(Integer.MAX_VALUE);
                Logger.setLoggerIfNo(new LazyLoggerFactory(() -> new FileLoggerEngine("./tracker_report_" + random + ".json")));

                accesses = new HashMap<>(MAP_INIT_SIZE);
                insideTracker = new InheritableThreadLocal<>();
            }
        }
    }

    public static synchronized void setDebugOutputStream(OutputStream s) {
        // TODO
        // can lead to deadlock if inside set logger a thread is triggered that causes accesses
        // so it will try to acquire the lock which is held. Here this might be shutdown of the old logger
        // use deadlock breaking
        Logger.setLogger(new StreamLoggerEngine(s));
    }

    public static void log(String s) {
        log(s, "MESSAGE");
    }

    public static void logEvent(String s) {
        if (!Tasks.getTask().hasEventID() || Tasks.getTask().getEventCounter() > MAX_EVENT_COUNT) return;
        log(s, "EVENT");
        Tasks.getTask().incrementEventID();
    }

    public static void log(String s, String tag) {
        init();
        insideTracker.set(true);
        try {
            synchronized (AccessTracker.class) {
                Logger.log(ReportGenerator.generateMessageJSON(s, tag));
            }
        } finally {
            insideTracker.remove();
        }
    }

    public static void writeAccess(AbstractFieldLocation f) {
        writeAccess(f, false);
    }

    public static void writeAccess(AbstractFieldLocation f, boolean valueIsNull) {
        if (!enabled) return;
        init();

        // To break the recursion; NULL check is neccessary (insideTracker is null when access is done from within InheritableThreadLocal)
        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            synchronized (AccessTracker.class) {
                if (!Tasks.hasTask()) return;
                if (Tasks.getTask().getInheritanceCount() > 0) return;
                FieldAccessMeta meta = accesses.get(f);
                if (meta == null) {
                    meta = new FieldAccessMeta();
                    accesses.put(f, meta);
                }
                if (valueIsNull) meta.clearWriters();
                else {
                    meta.registerWriter();
                    //Logger.getLogger().log(f.getLocation() + "\n" + Util.toString(Thread.currentThread().getStackTrace()) + "\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(e.toString());
        } finally {
            insideTracker.remove();
        }
    }

    public static void readAccess(AbstractFieldLocation field) {
        if (!enabled) return;
        init();

        // To break the recursion; NULL check is neccessary
        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            synchronized (AccessTracker.class) {
                FieldAccessMeta meta = accesses.get(field);
                if (meta == null) return;

                List<FieldWriter> l = meta.otherWriter();
                assert (l.size() <= 1);
                if (l.isEmpty()) return;

                FieldWriter writer = l.get(0);

                String eventID = field.getUniqueIdentifier() + meta.getWriteCount();
                Logger.log(
                        ReportGenerator.generateDetectionReportJSON(epoch,
                                Thread.currentThread().getId(),
                                Thread.currentThread().getStackTrace(),
                                field,
                                writer, meta, eventID));

                // Auto inheritance of task
                if (writer.getTask().getInheritanceCount() == 0) {
                    if (!Tasks.hasTask()) Tasks.startTask(writer.getTask());
                    else if (!Tasks.getTask().getTaskID().equals(writer.getTask().getTaskID()))
                        ; //TODO report collision
                    assert (Tasks.hasTask());
                    Tasks.getTask().addAsParentEventID(eventID);
                    if (!Tasks.getTask().hasInheritanceLocation(field)) Tasks.getTask().resetEventCounter();
                    Tasks.getTask().addInheritanceLocation(field);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(e.toString());
        } finally {
            insideTracker.remove();
        }
    }

    private static void registerArrayLocation(Object field, String location) {
        // this access point is only used to infer to which global field an array belongs
        // if an array field is read, the location is stored and later if the array reference is used,
        // the location can be looked up

        if (field == null) return; // a field read can give a null value
        if (!enabled) return;
        init();

        // To break the recursion; NULL check is neccessary
        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            if (!Tasks.hasTask()) return;
            // this is an approximate solution. It is possible that the writer that has the task does not read the global
            // field and only the reader does. In that case the array location cannot be inferred.
            // (e.g. writer manipulates a local array and stores it later)
            // but this is a lot faster and experiments showed it works in the most cases.
            ArrayFieldLocation.registerLocation(field, location);
        } finally {
            insideTracker.remove();
        }
    }

    public static synchronized void startTracking() {
        enabled = true;
    }

    public static synchronized void resetTracking() {
        synchronized (initLock) {
            accesses = new HashMap<>(MAP_INIT_SIZE);
            epoch++;
        }
    }


    // --------------------------------ACCESS HOOKS-------------------------------------

    // SPECIAL---------------------------
    public static void arrayCopy(Object src, int srcPos,
                                 Object dest, int destPos,
                                 int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);

        //TODO overtake old writers?

        Class sc = Object[].class;
        int srcEnd = srcPos + length;
        for (int i = srcPos; i < srcEnd; i++) readAccess(new ArrayFieldLocation(sc, src, i));

        Class dc = Object[].class;
        int destEnd = destPos + length;
        for (int i = destPos; i < destEnd; i++) writeAccess(new ArrayFieldLocation(dc, dest, i));
    }

    public static void readObjectArrayField(Object field, String location) {
        registerArrayLocation(field, location);
    }

    //-----------------------------------
    public static void readObject(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, Object.class, parent);
        readAccess(f);
    }

    public static void writeObject(Object parent, Object value, String location) {
        FieldLocation f = new FieldLocation(location, Object.class, parent);
        writeAccess(f, value == null);
    }

    public static int arrayReadInt(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(int[].class, arr, index);
        readAccess(f);
        return ((int[]) arr)[index];
    }

    public static void arrayWriteInt(Object arr, int index, int value) {
        ArrayFieldLocation f = new ArrayFieldLocation(int[].class, arr, index);
        writeAccess(f);

        ((int[]) arr)[index] = value;
    }

    public static Object arrayReadObject(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(Object[].class, arr, index);
        readAccess(f);
        return ((Object[]) arr)[index];
    }

    public static void arrayWriteObject(Object arr, int index, Object value) {
        ArrayFieldLocation f = new ArrayFieldLocation(Object[].class, arr, index);
        writeAccess(f, value == null);
        ((Object[]) arr)[index] = value;
    }

    public static void arrayWriteLong(Object arr, int index, long value) {
        ArrayFieldLocation f = new ArrayFieldLocation(long[].class, arr, index);
        writeAccess(f);
        ((long[]) arr)[index] = value;
    }

    public static long arrayReadLong(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(long[].class, arr, index);
        readAccess(f);
        return ((long[]) arr)[index];
    }

    public static void arrayWriteByteOrBoolean(Object arr, int index, byte value) {
        ArrayFieldLocation f = new ArrayFieldLocation(byte[].class, arr, index);
        writeAccess(f);
        if (arr instanceof byte[])
            ((byte[]) arr)[index] = value;
        else
            ((boolean[]) arr)[index] = value == 1;

    }

    public static byte arrayReadByteOrBoolean(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(byte[].class, arr, index);
        readAccess(f);
        if (arr instanceof byte[])
            return ((byte[]) arr)[index];
        else
            return (byte) (((boolean[]) arr)[index] ? 1 : 0);
    }

    public static void arrayWriteChar(Object arr, int index, char value) {
        ArrayFieldLocation f = new ArrayFieldLocation(char[].class, arr, index);
        writeAccess(f);
        ((char[]) arr)[index] = value;
    }

    public static char arrayReadChar(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(char[].class, arr, index);
        readAccess(f);
        return ((char[]) arr)[index];
    }

    public static void arrayWriteDouble(Object arr, int index, double value) {
        ArrayFieldLocation f = new ArrayFieldLocation(double[].class, arr, index);
        writeAccess(f);
        ((double[]) arr)[index] = value;
    }

    public static double arrayReadDouble(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(double[].class, arr, index);
        readAccess(f);
        return ((double[]) arr)[index];
    }

    public static void arrayWriteFloat(Object arr, int index, char value) {
        ArrayFieldLocation f = new ArrayFieldLocation(float[].class, arr, index);
        writeAccess(f);
        ((float[]) arr)[index] = value;
    }

    public static float arrayReadFloat(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(float[].class, arr, index);
        readAccess(f);
        return ((float[]) arr)[index];
    }

    public static void arrayWriteShort(Object arr, int index, short value) {
        ArrayFieldLocation f = new ArrayFieldLocation(short[].class, arr, index);
        writeAccess(f);
        ((short[]) arr)[index] = value;
    }

    public static short arrayReadShort(Object arr, int index) {
        ArrayFieldLocation f = new ArrayFieldLocation(short[].class, arr, index);
        readAccess(f);
        return ((short[]) arr)[index];
    }

    // FACADE METHODS-------------------------------------
    public static void startTask() {
        Tasks.startTask();
    }

    public static void stopTask() {
        Tasks.stopTask();
    }

    public static boolean hasTask() {
        return Tasks.hasTask();
    }

    public static void pauseTask() {
        Tasks.pauseTask();
    }

    public static void resumeTask() {
        Tasks.resumeTask();
    }
}