package boundarydetection.agent;

import boundarydetection.instrumentation.CodeInstrumenter;
import javassist.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;


public class Agent implements ClassFileTransformer {


    static final String[] EXCLUDES = new String[]{
            "java.lang.invoke",
            "javassist",
            "sun",
            //"sun.reflect"
            //"sun.instrument",
            "java.security",
            "java.lang.reflect",
            "java.lang.annotation",
            "[",
            "java.lang.instrument",
            "boundarydetection",
            "java.lang.ThreadLocal", //TODO
            "java.lang.ref",

            "edu.brown.cs", //TODO Detached baggage instru fails
            "org.apache.log4j",
//            "java.lang.Long",
//            "java.lang.Integer",
//            "java.lang.Short",
//            "java.lang.Byte",
//            "java.lang.Double",
//            "java.lang.Float",
//            "java.lang.Number",
//            "java.lang.Character",
//            "java.lang.Boolean",
//            "java.lang.String",
            "jdk.internal",
            "java.util.jar",
            "java.lang", //TODO
            "java.net",
            "java.io",
            "java.nio",
            "java",
            "com.sun",
            "org.aspectj",
            "org.jruby",
            "jnr",
            "org.joni",
            "org.apache.hbase.thirdparty.com.google.common.util.concurrent.Monitor", //TODO Stackmap in excpetion handler problem
            "org.joda",
            "org.apache.hadoop.hbase.regionserver.HRegion", //TODO Stackmap in excpetion handler problem
            "com.google.comm",
            "org.tinylog",
            "org.apache.zookeeper" //TODO there seems to be a caching/persistence mechanism that leads to persistece of instrumentation
                                   // only VM reset is possible then

            //TODO has to go, only debug
            // "org.apache.hadoop.hbase.master.HMaster",
            // "org.apache.hadoop.hbase.regionserver.HRegionServer",
            //TODO no exceptions,
            // no errors


    };


    static final String[] INCLUDES = new String[]{
            "client/Client",
            "java/util/ArrayDeque",
            //"java/util/AbstractCollection",

            "java/util/ArrayList",
            "java/util/LinkedList",
            //"java/util/AbstractList",
            //"java/util/AbstractCollection",
            "org/apache/hadoop/hbase",

            "java/util/concurrent/ArrayBlockingQueue"};
//            "java/util/concurrent/BlockingQueue",
//            "java/util/AbstractQueue",
//            "java/util/AbstractCollection"};


    public static void premain(final String agentArgument, final Instrumentation instrumentation) {
        instrumentation.addTransformer(new Agent(), true);

        // call for classes where agent is dependent on or that are used while bootstrapping
        for (Class c : instrumentation.getAllLoadedClasses()) {
            if ((true || isIncluded(c.getName())) && !isExcluded(c.getName())) {
                //System.out.println("RETRA: " + c.getName());
                try {
                    instrumentation.retransformClasses(c);
                } catch (UnmodifiableClassException e) {
                    System.out.println("Unmodifiable: " + c.getName() + ", continue");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Not found exception + JVM crash
     * https://stackoverflow.com/questions/51227630/java-lang-noclassdeffounderror-javassist
     */
    public byte[] transform(final ClassLoader loader, final String className, final Class clazz,
                            final java.security.ProtectionDomain domain, final byte[] bytes) {
        // System.out.println(className + " " + clazz);
        if (className == null) return bytes;

        //!isExcluded(className)
        if ((true || isIncluded(className)) && !isExcluded(className)) {
            try {
                return transformClass(className, clazz, bytes);
            } catch (NotFoundException e) {
                System.err.println("INST NOT FOUND " + className);
            } catch (Exception e) {
                System.err.println("INST ERROR " + className);
                e.printStackTrace();
            }
        }

        return bytes;
    }

    private byte[] transformClass(final String name, final Class cl, byte[] b) throws CannotCompileException, NotFoundException, IOException {
        ClassPool cp = ClassPool.getDefault();
        CtClass ctCl = cp.get(name.replace('/', '.'));
        if (ctCl.isInterface()) return b;
        System.out.println("INST: " + name);
        CtClass tracker = cp.get("boundarydetection.tracker.AccessTracker");

        CodeConverter conv = new CodeInstrumenter();
        conv.replaceArrayAccess(tracker, new CodeConverter.DefaultArrayAccessReplacementMethodNames());
        for (CtField field : ctCl.getDeclaredFields()) {
            conv.replaceFieldRead(field, tracker, "readObject");
            conv.replaceFieldWrite(field, tracker, "writeObject");
        }

        ctCl.instrument(conv);
        //ctCl.debugWriteFile();
        return ctCl.toBytecode();

    }

    //TODO can be optimized (binary search)
    private static boolean isExcluded(String name) {
        for (int i = 0; i < EXCLUDES.length; i++) {
            if (name.replace('/', '.').startsWith(EXCLUDES[i])) return true;
        }
        return false;
    }

    private static boolean isIncluded(String name) {
        for (int i = 0; i < INCLUDES.length; i++) {
            if (name.replace('.', '/').startsWith(INCLUDES[i])) return true;
        }
        return false;
    }


}
