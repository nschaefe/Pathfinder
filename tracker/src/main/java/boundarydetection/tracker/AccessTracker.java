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
    public static volatile int MAX_EVENT_COUNT = 75;

    // REMARK: recursion at runtime and while classloading can lead to complicated deadlocks (more on voice record)
    // is used to break the recursion. Internally used classes also access fields and arrays which leads to recursion.
    private static volatile InheritableThreadLocal<Boolean> insideTracker;
    private static boolean inited = false;
    private static Object initLock = new Object();

    private static HashMap<AbstractFieldLocation, FieldAccessMeta> accesses;
    private static int epoch = 0; //only as debug info
    private static volatile boolean enabled = false;

    private static volatile boolean eventLoggingEnabled = false;
    private static volatile boolean arrayCopyRedirectEnabled = false;

    private static long globalDetectionCounter;

    private static void init() {
        synchronized (initLock) {
            if (!inited) {
                // DO NOT PUT inited AT THE END (NECESSARY FOR RECURSION BREAKING)
                inited = true;

                globalDetectionCounter = 0;

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

    public static void logEvent(String s) {
        if (!enabled || !eventLoggingEnabled) return;
        init();

        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            synchronized (AccessTracker.class) {
                if (!Tasks.hasTask() || !Tasks.getTask().hasEventID() || Tasks.getTask().getEventCounter() > MAX_EVENT_COUNT)
                    return;
                Logger.log(ReportGenerator.generateMessageJSON(s, "EVENT"));
                Tasks.getTask().incrementEventID();
            }
        } finally {
            insideTracker.remove();
        }
    }

    public static void log(String s) {
        init();

        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            synchronized (AccessTracker.class) {
                Logger.log(ReportGenerator.generateMessageJSON(s, "MESSAGE"));
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
                writeAccessInner(f, valueIsNull);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(e.toString());
        } finally {
            insideTracker.remove();
        }
    }

    public static void writeAccessInner(AbstractFieldLocation f) {
        writeAccessInner(f, false);
    }

    private static void writeAccessInner(AbstractFieldLocation field, boolean valueIsNull) {
        if (!Tasks.hasTask()) return;
        if (Tasks.getTask().getInheritanceCount() > 0) return;
        FieldAccessMeta meta = accesses.get(field);
        if (meta == null) {
            meta = new FieldAccessMeta();
            accesses.put(field, meta);
        }
        if (valueIsNull) meta.clearWriters();
        else {
            meta.registerWriter();
            //Logger.getLogger().log(f.getLocation() + "\n" + Util.toString(Thread.currentThread().getStackTrace()) + "\n");
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
                readAccessInner(field);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(e.toString());
        } finally {
            insideTracker.remove();
        }
    }

    private static long readSerial = 0L;

    private static void readAccessInner(AbstractFieldLocation field) {
        FieldAccessMeta meta = accesses.get(field);
        if (meta == null) return;

        List<FieldWriter> l = meta.otherWriter();
        assert (l.size() <= 1);
        if (l.isEmpty()) return;

        FieldWriter writer = l.get(0);

        // a write can be read several times, so we use a global id to make all event ids unique
        String eventID = field.getUniqueIdentifier() + '_' + meta.getWriteCount() + '_' + (AccessTracker.globalDetectionCounter++);
        Logger.log(
                ReportGenerator.generateDetectionReportJSON(epoch,
                        readSerial++,
                        Thread.currentThread().getId(),
                        Thread.currentThread().getStackTrace(),
                        field,
                        writer, meta, eventID));

        // Auto inheritance of task
        if (writer.getTask().getInheritanceCount() == 0) {
            if (!Tasks.hasTask()) Tasks.startTask(writer.getTask());
            else if (!Tasks.getTask().getTaskID().equals(writer.getTask().getTaskID())) {
                // for now we just overtake the other task id. If we start a new Task that replaces the new one (new epoch)
                // this is fine and necessary, otherwise the new task is not propagated. If we have collision with another active task,
                // this is a problem that should be reported.
                // To do this we have to keep track of active tasks and only overtake if the old task died already
                // This can be future work.
                // TODO report warning
                Tasks.startTask(writer.getTask());
            }

            assert (Tasks.hasTask());
            Tasks.getTask().addAsParentEventID(eventID);
            //Tasks.getTask().getEventCounter() < 10 * MAX_EVENT_COUNT
            if (!Tasks.getTask().hasInheritanceLocation(field)) {
                Tasks.getTask().resetEventCounter();
            }
            Tasks.getTask().addInheritanceLocation(field);
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
            readSerial = 0;
        }
    }

    public static void enableEventLogging() {
        eventLoggingEnabled = true;
    }

    public static void disableEventLogging() {
        eventLoggingEnabled = false;
    }


    // --------------------------------ACCESS HOOKS-------------------------------------

    // SPECIAL---------------------------
    public static void arrayCopy(Object src, int srcPos,
                                 Object dest, int destPos,
                                 int length) {
        // TODO this strategy is incomplete and not really effective, we only track array copies under a running task
        // and do not inherit the old writer.
        // array copy is omnipresent but is rather unlikely and nondeterministic under a running task.
        // the results contain only about two entries out of >2000
        System.arraycopy(src, srcPos, dest, destPos, length);
        if (!arrayCopyRedirectEnabled) return;
        if (!(src instanceof Object[])) return;

        if (!enabled) return;
        init();

        // To break the recursion; NULL check is neccessary
        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            synchronized (AccessTracker.class) {
                if (!Tasks.hasTask()) return;

                Class sc = Object[].class;
                int srcEnd = srcPos + length;
                for (int i = srcPos; i < srcEnd; i++) readAccessInner(new ArrayFieldLocation(sc, src, i));

                Class dc = Object[].class;
                int destEnd = destPos + length;
                for (int i = destPos; i < destEnd; i++) writeAccessInner(new ArrayFieldLocation(dc, dest, i));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(e.toString());
        } finally {
            insideTracker.remove();
        }
        //TODO overtake old writers?

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