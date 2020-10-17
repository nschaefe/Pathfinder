package boundarydetection.tracker;

import boundarydetection.tracker.tasks.Task;
import boundarydetection.tracker.tasks.TaskCollisionException;
import boundarydetection.tracker.tasks.Tasks;
import boundarydetection.tracker.util.Pair;
import boundarydetection.tracker.util.logging.*;
import sun.misc.Unsafe;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class AccessTracker {

    private static final int MAP_INIT_SIZE = 655360;
    public static volatile int MAX_EVENT_COUNT = 75;

    // REMARK: recursion at runtime and while classloading can lead to complicated deadlocks (more on voice record)
    // is used to break the recursion. Internally used classes also access fields and arrays which leads to recursion.
    private static volatile InheritableThreadLocal<Boolean> insideTracker;
    private static boolean inited = false;
    private static Object initLock = new Object();

    private static HashMap<AbstractFieldLocation, FieldAccessMeta> accesses;
    private static int epoch = 0; //only as debug info
    private static volatile boolean enabled = false;

    private static volatile boolean readerEventLoggingEnabled = false;
    private static volatile boolean writerEventLoggingEnabled = false;
    private static volatile boolean arrayCopyRedirectEnabled = false;
    private static volatile boolean autoTaskInheritance = false;

    private static final boolean minimalTracking = false;

    private static long globalDetectionCounter;

    private static void init() {
        synchronized (initLock) {
            if (!inited) {
                // DO NOT PUT inited AT THE END (NECESSARY FOR RECURSION BREAKING)
                inited = true;

                globalDetectionCounter = 0;

                int random = (new Random()).nextInt(Integer.MAX_VALUE);
                // avg log entries have 4000 bytes
                Logger.setLoggerIfNo(new LazyLoggerFactory(() -> new HeavyBufferFileLoggerEngine(32768 * 4000, "./tracker_report_" + random + ".json")));
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        try {
                            Logger.shutdown();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });

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
        if (!enabled || (!writerEventLoggingEnabled && !readerEventLoggingEnabled)) return;
        init();

        if (insideTracker == null || insideTracker.get() != null) return;
        insideTracker.set(true);
        try {
            synchronized (AccessTracker.class) {
                //TODO the logging behavior of the two is not consistent. There are only build for specific use cases:
                // readerLogging: logging of executed methods of the reader after hitting a write/read relation, only available in combination with auto task inheritance
                // writerLogging: logging of all executed methods of the writer after starting a task.

                if (writerEventLoggingEnabled) writerLogging(s);
                if (readerEventLoggingEnabled) readerLogging(s);
            }
        } finally {
            insideTracker.remove();
        }
    }

    private static void readerLogging(String s) {
        if (!Tasks.hasTask() || !Tasks.getTask().hasEventID() || Tasks.getTask().getEventCounter() > MAX_EVENT_COUNT)
            return;
        Logger.log(ReportGenerator.generateMessageJSON(s, "EVENT"));
        Tasks.getTask().incrementEventID();
    }

    private static void writerLogging(String s) {
        if (!Tasks.hasTask() || !Tasks.getTask().hasWriteCapability()) return;

        if (!Tasks.getTask().hasEventID()) {
            int random = (new Random()).nextInt(Integer.MAX_VALUE);
            Tasks.getTask().addAsParentEventID(Thread.currentThread().getId() + "_" + random);
        }
        Logger.log(ReportGenerator.generateMessageJSON(s, "WRITER_EVENT"));
        Tasks.getTask().incrementEventID();
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
        Logger.log(
                ReportGenerator.generateDetectionReportJSON(epoch,
                        readSerial++,
                        Thread.currentThread().getId(),
                        Thread.currentThread().getStackTrace(),
                        Tasks.getTask(),
                        field,
                        writer, meta));

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
    public static void enableReaderEventLogging() {
        readerEventLoggingEnabled = true;
    }

    public static void disableReaderEventLogging() {
        readerEventLoggingEnabled = false;
    }

    public static void enableWriterEventLogging() {
        writerEventLoggingEnabled = true;
    }

    public static void disableWriterEventLogging() {
        writerEventLoggingEnabled = false;
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

    /*
    This method was only introduced for array location inference.
    The array object of the field read is captured what is not the case for the other tracking methods
     */
    public static void readArrayField(Object value, String location) {
        registerArrayLocation(value, location);
    }

    public static void readObject(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, Object.class, parent);
        readAccess(f);
    }

    public static void writeObject(Object parent, Object value, String location) {
        if (value != null && value.getClass().isArray()) registerArrayLocation(value, location);
        FieldLocation f = new FieldLocation(location, Object.class, parent);
        writeAccess(f, value == null);
    }

    public static void readI(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, int.class, parent);
        readAccess(f);
    }

    public static void writeI(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, int.class, parent);
        writeAccess(f, false);
    }

    public static void readD(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, double.class, parent);
        readAccess(f);
    }

    public static void writeD(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, double.class, parent);
        writeAccess(f, false);
    }

    public static void readF(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, float.class, parent);
        readAccess(f);
    }

    public static void writeF(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, float.class, parent);
        writeAccess(f, false);
    }

    public static void readJ(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, long.class, parent);
        readAccess(f);
    }

    public static void writeJ(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, long.class, parent);
        writeAccess(f, false);
    }

    public static void readZ(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, boolean.class, parent);
        readAccess(f);
    }

    public static void writeZ(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, boolean.class, parent);
        writeAccess(f, false);
    }

    public static void readB(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, byte.class, parent);
        readAccess(f);
    }

    public static void writeB(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, byte.class, parent);
        writeAccess(f, false);
    }

    public static void readC(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, char.class, parent);
        readAccess(f);
    }

    public static void writeC(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, char.class, parent);
        writeAccess(f, false);
    }

    public static void readS(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, short.class, parent);
        readAccess(f);
    }

    public static void writeS(Object parent, String location) {
        if (minimalTracking) return;
        FieldLocation f = new FieldLocation(location, short.class, parent);
        writeAccess(f, false);
    }

    public static int arrayReadInt(Object arr, int index) {
        int val = ((int[]) arr)[index];
        if (minimalTracking) return val;
        ArrayFieldLocation f = new ArrayFieldLocation(int[].class, arr, index);
        readAccess(f);
        return val;
    }

    public static void arrayWriteInt(Object arr, int index, int value) {
        ((int[]) arr)[index] = value;
        if (minimalTracking) return;
        ArrayFieldLocation f = new ArrayFieldLocation(int[].class, arr, index);
        writeAccess(f);
    }

    public static Object arrayReadObject(Object arr, int index) {
        Object val = ((Object[]) arr)[index];
        ArrayFieldLocation f = new ArrayFieldLocation(Object[].class, arr, index);
        readAccess(f);
        return val;
    }

    public static void arrayWriteObject(Object arr, int index, Object value) {
        ((Object[]) arr)[index] = value;
        ArrayFieldLocation f = new ArrayFieldLocation(Object[].class, arr, index);
        writeAccess(f, value == null);
    }

    public static void arrayWriteLong(Object arr, int index, long value) {
        ((long[]) arr)[index] = value;
        if (minimalTracking) return;
        ArrayFieldLocation f = new ArrayFieldLocation(long[].class, arr, index);
        writeAccess(f);
    }

    public static long arrayReadLong(Object arr, int index) {
        long val = ((long[]) arr)[index];
        if (minimalTracking) return val;
        ArrayFieldLocation f = new ArrayFieldLocation(long[].class, arr, index);
        readAccess(f);
        return val;
    }

    public static void arrayWriteByteOrBoolean(Object arr, int index, byte value) {
        Class type;
        if (arr instanceof byte[]) {
            ((byte[]) arr)[index] = value;
            type = byte[].class;
        } else {
            ((boolean[]) arr)[index] = value == 1;
            type = boolean[].class;
        }
        if (minimalTracking) return;
        ArrayFieldLocation f = new ArrayFieldLocation(type, arr, index);
        writeAccess(f);
    }

    public static byte arrayReadByteOrBoolean(Object arr, int index) {
        Class type;
        byte val;
        if (arr instanceof byte[]) {
            val = ((byte[]) arr)[index];
            type = byte[].class;
        } else {
            val = (byte) (((boolean[]) arr)[index] ? 1 : 0);
            type = boolean[].class;
        }
        if (minimalTracking) return val;
        ArrayFieldLocation f = new ArrayFieldLocation(type, arr, index);
        readAccess(f);
        return val;
    }

    public static void arrayWriteChar(Object arr, int index, char value) {
        ((char[]) arr)[index] = value;
        if (minimalTracking) return;
        ArrayFieldLocation f = new ArrayFieldLocation(char[].class, arr, index);
        writeAccess(f);
    }

    public static char arrayReadChar(Object arr, int index) {
        char val = ((char[]) arr)[index];
        if (minimalTracking) return val;
        ArrayFieldLocation f = new ArrayFieldLocation(char[].class, arr, index);
        readAccess(f);
        return val;
    }

    public static void arrayWriteDouble(Object arr, int index, double value) {
        ((double[]) arr)[index] = value;
        if (minimalTracking) return;
        ArrayFieldLocation f = new ArrayFieldLocation(double[].class, arr, index);
        writeAccess(f);
    }

    public static double arrayReadDouble(Object arr, int index) {
        double val = ((double[]) arr)[index];
        if (minimalTracking) return val;
        ArrayFieldLocation f = new ArrayFieldLocation(double[].class, arr, index);
        readAccess(f);
        return val;
    }

    public static void arrayWriteFloat(Object arr, int index, float value) {
        ((float[]) arr)[index] = value;
        if (minimalTracking) return;
        ArrayFieldLocation f = new ArrayFieldLocation(float[].class, arr, index);
        writeAccess(f);
    }

    public static float arrayReadFloat(Object arr, int index) {
        float val = ((float[]) arr)[index];
        if (minimalTracking) return val;
        ArrayFieldLocation f = new ArrayFieldLocation(float[].class, arr, index);
        readAccess(f);
        return val;
    }

    public static void arrayWriteShort(Object arr, int index, short value) {
        ((short[]) arr)[index] = value;
        if (minimalTracking) return;
        ArrayFieldLocation f = new ArrayFieldLocation(short[].class, arr, index);
        writeAccess(f);
    }

    public static short arrayReadShort(Object arr, int index) {
        short val = ((short[]) arr)[index];
        if (minimalTracking) return val;
        ArrayFieldLocation f = new ArrayFieldLocation(short[].class, arr, index);
        readAccess(f);
        return val;
    }

    //--------------- UNSAFE
    private static volatile Unsafe UNSAFE;

    public static synchronized void initUnsafe() {
        if (UNSAFE == null) UNSAFE = sun.misc.Unsafe.getUnsafe();
    }

    private static HashMap<Pair<String, Long>, String> aliasForOffset = new HashMap<Pair<String, Long>, String>();

    private static void setAlias(long offset, Class<?> c, Field f) {
        String className = c.getName();
        Pair p = new Pair<>(className, offset);
        aliasForOffset.put(p, className + '.' + f.getName());
    }

    private static String getAlias(long offset, Class<?> c) {
        String className = c.getName();
        Pair p = new Pair<>(className, offset);
        return aliasForOffset.get(p);
    }

    public static long objectFieldOffset(Field f) {
        initUnsafe();
        long valueOffset = UNSAFE.objectFieldOffset(f);
        //TODO this is called before thread locals are loaded. We could try to pause at least later when they are available
        //pauseTask();
        setAlias(valueOffset, f.getDeclaringClass(), f);
        //resumeTask();
        return valueOffset;
    }

    private static void registerWrite(Object o, long offset, Object val) {
        Class<?> clas = o.getClass();
        if (!clas.isArray()) {
            pauseTask();
            //TODO check for task and enabled etc
            String s = getAlias(offset, clas);
            while (s == null && (clas = clas.getSuperclass()) != null) {
                s = getAlias(offset, clas);
            }
            resumeTask();
            // we do not track a read here because this method is not used to read a value, expected value is given
            if (s != null) writeAccess(new FieldLocation(s, Object.class, o), val == null);
        }
    }

    public static boolean compareAndSwapObject(Object o, long l, Object a, Object b) {
        initUnsafe();
        boolean re = UNSAFE.compareAndSwapObject(o, l, a, b);
        if (!enabled) return re;
        if (re) registerWrite(o, l, b);
        return re;
    }

    public static void putOrderedObject(Object o, long l, Object val) {
        initUnsafe();
        UNSAFE.putOrderedObject(o, l, val);
        if (!enabled) return;
        registerWrite(o, l, val);
    }

    public static void putObject(Object o, long l, Object val) {
        initUnsafe();
        UNSAFE.putObject(o, l, val);
        if (!enabled) return;
        registerWrite(o, l, val);
    }

    //---------------

    // FACADE METHODS-------------------------------------
    public static void startTask() {
        AccessTracker.startTracking();
        Tasks.startTask();
    }

    public static void startTask(String tag) {
        AccessTracker.startTracking();
        Tasks.startTask(tag);
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

    public static void join(Task t, String tag) {
        try {
            if (t != null) AccessTracker.startTracking();
            Tasks.join(t, tag);
        } catch (TaskCollisionException e) {
            init();
            Logger.log(e.toString() + "\n" + Arrays.toString(e.getStackTrace()), "ERROR");
        }
    }

    public static void join(Task t) {
        try {
            if (t != null) AccessTracker.startTracking();
            Tasks.join(t);
        } catch (TaskCollisionException e) {
            init();
            Logger.log(e.toString() + "\n" + Arrays.toString(e.getStackTrace()), "ERROR");
        }
    }

    public static void tryJoin(Task t) throws TaskCollisionException {
        Tasks.join(t);
    }

    public static void discard() {
        Tasks.discard();
    }

    public static Task fork() {
        return Tasks.fork();
    }

    public static String serialize() {
        return Tasks.serialize();
    }

    public static Task deserialize(String s) {
        return Tasks.deserialize(s);
    }

}