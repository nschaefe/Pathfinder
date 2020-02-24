package boundarydetection.tracker;

import edu.brown.cs.systems.xtrace.XTraceBaggageInterface;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class AccessTracker {

    //TODO it would be useful to have a no-false-positives and a no-false-negative mode
    // --> no-false-positives only gives sensible boundaries that need to be supported, but could miss some cases
    //     no-false-negatives is the other way round, complete but much
    // no-false-positives:  do not support array as fields (stretching),
    //                      do not consider writes of newly created objects in constructors (init cases)
    //TODO support field access if the object is an array. that gives a false positive everytime the array
    // is streched, but this could lead to false negatives, so we should have an option to
    // switch it on
    //TODO mark fieldwriter with readers, if we already saw this reader reading from the same writer case, do not report again (currently solved by distinct.py)
    //TODO refactor, keep complexity of redundancy recognition outside of basic recognition
    //TODO reading and writing an object to another array index or another array (copied), does
    // not loose old writers, if we associate writers with an object.
    // but we have to recognize positions when a writer overtake is sensible. If the copier writes the object, it will take the place of the old writer
    // Load balancing of requests over workers gives false positives, if we do it every time.
    // So only if we copy an array that ends up being in the same location as the old one, but this is not trivial to detect.
    // -> for now it is ok to rely on the nondeterminism argument when arrays are copied.
    // easy way for refactoring to object perspective-> We currently look at accesses to ArrayFields or Fields (represent locations) and track accesses to them
    // we just have to redefine what an access location is. Instead of fields and arrays we say objects if we have
    // an object at hand or the field location (array index, field) if it is a primitive.

    //TODO use weak refs to get rid of locations (relative to objects) that are dead and should be destroyed
    // we have to take care of access counters, if we destroy dynamic locations and want to preserve code locations
    // -> just mark them as archived and destroy parent but keep the rest

    // TODO REFACTOR: Accesscontroller as accesspoint from outside
    // Access tracker as buisness tracker model that makes the decisions, move code from field access meta to tracker
    // field access meta as pure datastructure/infrastructure

    //TODO support for fields with other types than object e.g. for array as field (access of the array object)
    //TODO bytecode instrumentation, related problems and solution document

    private static HashMap<AbstractFieldLocation, FieldAccessMeta> accesses;

    // REMARK: recursion at runtime and while classloading can lead to complicated deadlocks (more on voice record)
    // is used to break the recursion. Internally used classes also access fields and arrays which leads to recursion.
    private static volatile InheritableThreadLocal<Boolean> insideTracker;
    private static volatile boolean inited = false;
    private static volatile boolean enabled = false;

    private static void init() {
        if (!inited) {
            // DO NOT PUT inited AT THE END (NECESSARY FOR RECURSION BREAKING)
            inited = true;

            int random = (new Random()).nextInt(Integer.MAX_VALUE);
            Logger.configureLogger("./tracker_report_" + random + ".json");

            accesses = new HashMap<>();
            insideTracker = new InheritableThreadLocal<>();
        }
    }

    public static void startTracking() {
        enabled = true;
    }

    public static void writeAccess(AbstractFieldLocation f) {
        writeAccess(f, false);
    }

    public static void writeAccess(AbstractFieldLocation f, boolean valueIsNull) {
        if (!enabled) return;
        init();
        // To break the recursion; NULL check is neccessary (insideTracker is null when access is done from within InheritableThreadLocal)
        if (insideTracker == null || insideTracker.get() != null) return;
        try {
            synchronized (AccessTracker.class) {
                insideTracker.set(true);
                if (!XTraceBaggageInterface.hasTaskID()) return;
                FieldAccessMeta meta = accesses.get(f);
                if (meta == null) {
                    meta = new FieldAccessMeta();
                    accesses.put(f, meta);
                }
                if (valueIsNull) meta.clearWriters();
                else meta.registerWriter();
            }
        } finally {
            insideTracker.remove();
        }
    }

    public static void readAccess(AbstractFieldLocation f) {
        if (!enabled) return;
        init();
        // To break the recursion; NULL check is neccessary
        if (insideTracker == null || insideTracker.get() != null) return;
        try {
            synchronized (AccessTracker.class) {
                insideTracker.set(true);
                // System.out.println("READ: " + toString(Thread.currentThread().getStackTrace()));
                FieldAccessMeta meta = accesses.get(f);
                if (meta == null) return;

                List<FieldWriter> l = meta.otherWriter();
                if (l.isEmpty()) return;
                assert (l.size() <= 1);

                StringBuilder s = new StringBuilder();
                FieldWriter w = l.get(0);

                Logger.getLogger().log(
                        ReportGenerator.generateDetectionReportJSON(
                                Thread.currentThread().getId(),
                                Thread.currentThread().getStackTrace(),
                                f,
                                w, meta));
            }
        } finally {
            insideTracker.remove();
        }
    }

    // ACCESS HOOKS---------------------------------------------
    public static void readObject(Object parent, String location) {
        FieldLocation f = new FieldLocation(location, Object.class, parent);
        readAccess(f);
    }

//    public static Object readObject(Object value, Object parent, String location) {
//        Field f = new Field(location, Object.class, parent);
//        readAccess(f);
//        return  value;
//    }

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


}