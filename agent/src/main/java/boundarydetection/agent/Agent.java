package boundarydetection.agent;

import boundarydetection.instrumentation.CodeInstrumenter;
import boundarydetection.instrumentation.Util;
import javassist.*;
import javassist.build.JavassistBuildException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.function.Predicate;


public class Agent implements ClassFileTransformer, javassist.build.IClassTransformer {


    private static final String[] EXCLUDES = new String[]{
            // JAVA INTERNALS
            "[",
            "sun",
            "com.sun",
            "jdk.internal",
//            "java.lang.ThreadLocal",
//            "java.lang.ref",
//            "java.lang.invoke",
//            "java.lang.reflect",
//            "java.lang.instrument",
//            "java.lang.annotation",
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

            // AGENT/TRACKER INTERNALS
            "javassist",
            "boundarydetection",
            "edu.brown.cs",
            "org.tinylog",
            "com.fasterxml.jackson",

            // APPLICATION PACKAGES BLACKLIST (HBase)
            "org.apache.log4j",
            "org.slf4j",
            "org.aspectj",
            "org.jruby",
            "jnr",
            "org.apache.hbase.thirdparty.com.google.common.util.concurrent.Monitor", //TODO Stackmap in excpetion handler problem
            "org.apache.hadoop.hbase.regionserver.HRegion", //TODO Stackmap in excpetion handler problem
            "org.apache.zookeeper", //TODO there seems to be a caching/persistence mechanism that leads to persistece of instrumentation, only VM reset is possible then
            "org.apache.htrace", //built in tracing

            // APPLICATION PACKAGES BLACKLIST (JUnit)
            "org.junit",

            //COVERED BY STATIC RT INSTRUMENTATION
            "java"
    };


    private static final String[] INCLUDES = new String[]{
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


    private static final String HOOK_CLASS = "boundarydetection.tracker.AccessTracker";
    private ClassPool cp;
    private static boolean logging_enabled = true;

    public static void premain(final String agentArgument, final Instrumentation instrumentation) {
        if (agentArgument != null) configure(agentArgument);
        instrumentation.addTransformer(new Agent(), true);
        // call for classes where agent is dependent on or that are used while bootstrapping
        for (Class c : instrumentation.getAllLoadedClasses()) {
            if (shouldTransform(c.getName())) {
                //if(logging_enabled) System.out.println("RETRA: " + c.getName());
                try {
                    instrumentation.retransformClasses(c);
                } catch (UnmodifiableClassException e) {
                    System.out.println("Unmodifiable: " + c.getName() + ", continue");
                    e.printStackTrace();
                }
            }
        }
    }

    public static void configure(String argumentString) {
        //TODO
        logging_enabled = false;
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
        if (shouldTransform(className)) {
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
        transformClass(ctCl);

        //ctCl.debugWriteFile();
        return ctCl.toBytecode();

    }

    private void transformClass(CtClass ctCl) throws CannotCompileException, NotFoundException {
        if (ctCl.isInterface()) return;

        ClassPool cp = getClassPool();
        if (logging_enabled) System.out.println("INST: " + ctCl.getName());
        CtClass tracker = null;
        tracker = cp.get(HOOK_CLASS);

        replaceArrayCopy(ctCl);

        CodeInstrumenter conv = new CodeInstrumenter();
        // adds transformers at the head (so reverse order)
        conv.replaceArrayAccess(tracker, new CodeConverter.DefaultArrayAccessReplacementMethodNames());

        Predicate<String> filter = (s) -> Util.isSingleObjectSignature(s);
        conv.instrumentFieldRead(tracker, filter);
        conv.instrumentFieldWrite(tracker, filter);
        conv.reformatConstructor();
        ctCl.instrument(conv);

        logMethodBeginAsEvent(ctCl);
    }

    public void logMethodBeginAsEvent(CtClass cl) throws CannotCompileException {
        if (cl.isInterface()) return;
        CtBehavior[] m = cl.getDeclaredBehaviors();
        for (CtBehavior b : m) {
            if (isNative(b) || isAbstract(b)) continue;
            b.insertBefore(HOOK_CLASS + ".logEvent(\"" + b.getLongName() + "\");"); //TODO caller not contained
        }
    }

    public static boolean isNative(CtBehavior method) {
        return Modifier.isNative(method.getModifiers());
    }

    public static boolean isAbstract(CtBehavior method) {
        return Modifier.isAbstract(method.getModifiers());
    }

    private void replaceArrayCopy(CtClass ctCl) throws CannotCompileException {
        // invokestatic  #65                 // Method java/lang/System.arraycopy:(Ljava/lang/Object;ILjava/lang/Object;II)V
        ctCl.instrument(new ExprEditor() {

            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getClassName().equals("java.lang.System") &&
                        m.getSignature().equals("(Ljava/lang/Object;ILjava/lang/Object;II)V") &&
                        m.getMethodName().equals("arraycopy")) {
                    m.replace(HOOK_CLASS + ".arrayCopy($$);");
                }
            }
        });
    }


    public void instClassLoader(CtClass ctCl) throws NotFoundException, CannotCompileException {
        if (logging_enabled) System.out.println("INST: " + ctCl.getName());
        CtMethod m = ctCl.getMethod("loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;");
        //CtMethod m = ctCl.getMethod("loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        instloading(m);
    }

    private void instloading(CtMethod m) throws CannotCompileException {
        m.insertBefore("boundarydetection.tracker.AccessTracker.pauseTask();");
        m.insertAfter("boundarydetection.tracker.AccessTracker.resumeTask();", true);
    }

    public ClassPool getClassPool() throws NotFoundException {
        if (cp == null) {
            cp = ClassPool.getDefault();
            cp.insertClassPath(new LoaderClassPath(this.getClass().getClassLoader()));
        }
        return cp;
    }

    public static boolean shouldTransform(String clName) {
        return (true || isIncluded(clName)) && !isExcluded(clName);
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


    // -------ACCESS POINTS FOR STATIC INSTRUMENTATION (MAVEN PLUGIN)------
    @Override
    public void applyTransformations(CtClass ctCl) throws JavassistBuildException {
        try {
            transformClass(ctCl);
        } catch (NotFoundException | CannotCompileException e) {
            JavassistBuildException ex = new JavassistBuildException(e);
            throw ex;
        }
    }

    @Override
    public boolean shouldTransform(CtClass ctClass) throws JavassistBuildException {
        return shouldTransform(ctClass.getName());
    }
}
