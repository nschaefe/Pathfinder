package boundarydetection.agent;

import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/**
 *
 */
public class Agent implements ClassFileTransformer {

    /**
     * classes to always not to instrument
     */
    static final String[] EXCLUDES = new String[]{};

    static final String[] INCLUDES = new String[]{"client/Client"};

    /**
     * add agent
     */
    public static void premain(final String agentArgument, final Instrumentation instrumentation) {
        instrumentation.addTransformer(new Agent());
    }

    /**
     * instrument class
     */
    public byte[] transform(final ClassLoader loader, final String className, final Class clazz,
                            final java.security.ProtectionDomain domain, final byte[] bytes) {

        // System.out.println(className);

//        for (int i = 0; i < EXCLUDES.length; i++) {
//            if (className.startsWith(EXCLUDES[i])) {
//                return bytes;
//            }
//        }
        for (int i = 0; i < INCLUDES.length; i++) {
            if (className.startsWith(INCLUDES[i])) {
                return transformClass(className, clazz, bytes);
            }
        }

        return bytes;
    }

    /**
     * instrument class with javasisst
     */
    private byte[] transformClass(final String name, final Class cl, byte[] b) {
        ClassPool cp = ClassPool.getDefault();
        try {
            CtClass ctCl = cp.get(name.replace('/', '.'));
            CtClass agentCl = cp.get(this.getClass().getName());

            CodeConverter conv = new CodeConverter();
//            for (CtField f : ctCl.getDeclaredFields()) {
//                //conv.replaceFieldRead(f, agentCl, "readField");
//
//            }
            conv.replaceArrayAccess(agentCl, new CodeConverter.DefaultArrayAccessReplacementMethodNames());
            ctCl.instrument(conv);
            return ctCl.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return b;
    }

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

    public synchronized static void arrayWrite(ArrayField f) {
        FieldAccessMeta meta = accesses.get(f);
        if (meta == null) {
            meta = new FieldAccessMeta();
            accesses.put(f, meta);
        }

        meta.registerWriter();
    }

    public synchronized static void arrayRead(ArrayField f) {
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
//  arrayReadObject
//  arrayWriteObject
//  arrayReadShort
//  arrayWriteShort

}
