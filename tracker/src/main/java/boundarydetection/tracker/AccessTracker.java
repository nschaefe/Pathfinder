package boundarydetection.tracker;

import boundarydetection.tracker.tasks.Task;
import boundarydetection.tracker.tasks.TaskCollisionException;
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
    private static volatile boolean autoTaskInheritance = false;

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
        if (!Tasks.getTask().hasWriteCapability()) return;
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
                        Tasks.getTask(),
                        field,
                        writer, meta, eventID));


        if (!autoTaskInheritance) return;
        // Auto inheritance of task
        if (writer.getTask().getAutoInheritanceCount() == 0) { //inherit only one step down
            if (!Tasks.hasTask()) Tasks.inheritTask(writer.getTask());
            else {
                // there is already another task present in the target thread
                // undesired situations that need to be reported

                // if there is another main task running (inheritance count = 0)
                // we should not inherit
                Task present = Tasks.getTask();
                if (present.getAutoInheritanceCount() == 0) {
                    Logger.log(ReportGenerator.generateAutoInheritanceMessageJSON("Auto task inheritance failed. There is already a main task present in the target thread", "ERROR", Thread.currentThread().getStackTrace(), writer.getStackTrace()));
                    return;
                }

                // if there is already a reader task running (inheritance count > 0)
                // we should only override if the bounded parent task is dead already
                if (present.getAutoInheritanceCount() > 0 && !present.getParentTask().getSubTraceID().equals(writer.getTask().getSubTraceID()) && present.getParentTask().isAlive()) {
                    Logger.log(ReportGenerator.generateAutoInheritanceMessageJSON(" Auto task inheritance: task is inherited to a thread that still has another inherited task running which parent is still alive", "WARNING", Thread.currentThread().getStackTrace(), writer.getStackTrace()));
                    return;
                }

                // else if there is already another reader task running (inheritance count > 0)
                // but parent already died, we overtake the task of the writer. This is when a new task was started and replaces
                // the old inherited task now
                if (present.getAutoInheritanceCount() > 0 && !present.getParentTask().getSubTraceID().equals(writer.getTask().getSubTraceID()) && !present.getParentTask().isAlive()) {
                    Tasks.inheritTask(writer.getTask());
                }
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

    private static void registerArrayLocation(Object value, String location) {
        // this access point is only used to infer to which global field an array belongs
        // if an array field is read, the location is stored and later if the array reference is used,
        // the location can be looked up

        if (value == null) return; // a field read can give a null value
        if (!enabled) return;
        init();

        // To break the recursion; NULL check is neccessary
        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            if (!Tasks.hasTask()) return;
            // To only track under a task is an approximate solution if we only register on reads.
            // It is possible that the writer that has the task does not read the global
            // field and only the reader does. In that case the array location cannot be inferred.
            // (e.g. writer manipulates a local array and stores it later)
            // But usually the array is already there and is used as infrastructure
            // and this is a lot faster and experiments showed it works in the most cases.
            ArrayFieldLocation.registerLocation(value, location);
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

    //TODO rename this to set... so we do not need two methods
    public static void enableEventLogging() {
        eventLoggingEnabled = true;
    }

    public static void disableEventLogging() {
        eventLoggingEnabled = false;
    }


    public static void enableAutoTaskInheritance() {
        autoTaskInheritance = true;
    }

    public static void disableAutoTaskInheritance() {
        autoTaskInheritance = false;
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


    //-----------------------------------

    //TODO add on writeObject field for more coverage
    public static void readArrayField(Object value, String location) {
        registerArrayLocation(value, location);
    }

    public static void writeArrayField(Object parent, Object value, String location) {
    }

    public static void readObject(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, Object.class, parent);
        readAccess(f);
    }

    public static void writeObject(Object parent, Object value, String location) {
        FieldLocation f = new FieldLocation(location, Object.class, parent);
        writeAccess(f, value == null);
    }

    public static void readI(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, int.class, parent);
        readAccess(f);
    }

    public static void writeI(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, int.class, parent);
        writeAccess(f, false);
    }

    public static void readD(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, double.class, parent);
        readAccess(f);
    }

    public static void writeD(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, double.class, parent);
        writeAccess(f, false);
    }

    public static void readF(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, float.class, parent);
        readAccess(f);
    }

    public static void writeF(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, float.class, parent);
        writeAccess(f, false);
    }

    public static void readJ(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, long.class, parent);
        readAccess(f);
    }

    public static void writeJ(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, long.class, parent);
        writeAccess(f, false);
    }

    public static void readZ(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, boolean.class, parent);
        readAccess(f);
    }

    public static void writeZ(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, boolean.class, parent);
        writeAccess(f, false);
    }

    public static void readB(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, byte.class, parent);
        readAccess(f);
    }

    public static void writeB(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, byte.class, parent);
        writeAccess(f, false);
    }

    public static void readC(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, char.class, parent);
        readAccess(f);
    }

    public static void writeC(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, char.class, parent);
        writeAccess(f, false);
    }

    public static void readS(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, short.class, parent);
        readAccess(f);
    }

    public static void writeS(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, short.class, parent);
        writeAccess(f, false);
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

    public static Task getTask() {
        return Tasks.getTask();
    }

    public static void pauseTask() {
        Tasks.pauseTask();
    }

    public static void resumeTask() {
        Tasks.resumeTask();
    }

    public static void join(Task t) {
        try {
            Tasks.join(t);
        } catch (TaskCollisionException e) {
            init();
            Logger.log(e.toString(), "ERROR");
        }
    }

    public static void discard() {
        Tasks.discard();
    }

    public static Task fork() {
        return Tasks.fork();
    }
}