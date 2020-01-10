package boundarydetection.agent;

import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;


/**
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

        System.out.println(className);

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
        System.out.println("array read by: "+Thread.currentThread().getId());
        return ((int[])arr)[index];

    }

    public static void arrayWriteInt(Object arr, int index, int value) {
        System.out.println("array write by: "+Thread.currentThread().getId());
        ((int[])arr)[index] = value;
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
//  arrayReadObject
//  arrayWriteObject
//  arrayReadShort
//  arrayWriteShort

}
