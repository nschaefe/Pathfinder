package boundarydetection.agent;

import boundarydetection.instrumentation.CodeInstrumenter;
import boundarydetection.instrumentation.Util;
import javassist.*;
import javassist.build.JavassistBuildException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.function.Predicate;


public class Agent implements ClassFileTransformer {


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
            "org.junit"
    };

    private static final String[] STATIC_INSTRUMENT = new String[]{
            //COVERED BY STATIC RT INSTRUMENTATION
            //TODO static instrumentation does not refer to this list here and might instrument a subset.
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
            if (dynamicallyTransform(c.getName())) {
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
        if (dynamicallyTransform(className)) {
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

    public void transformClass(CtClass ctCl) throws CannotCompileException, NotFoundException {
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

    private void logMethodBeginAsEvent(CtClass cl) throws CannotCompileException {
        if (cl.isInterface()) return;
        CtBehavior[] m = cl.getDeclaredBehaviors();
        for (CtBehavior b : m) {
            if (Util.isNative(b) || Util.isAbstract(b)) continue;
            b.insertBefore(HOOK_CLASS + ".logEvent(\"" + b.getLongName() + "\");"); //TODO caller not contained
        }
    }

    public void instClassLoader(CtClass ctCl) throws NotFoundException, CannotCompileException {
        if (logging_enabled) System.out.println("INST: " + ctCl.getName());
        //TODO assert is classloader class
        CtMethod m = ctCl.getMethod("loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;");
        //CtMethod m = ctCl.getMethod("loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        m.insertBefore("boundarydetection.tracker.AccessTracker.pauseTask();");
        m.insertAfter("boundarydetection.tracker.AccessTracker.resumeTask();", true);
    }

    public void instLambdaMetaFactory(CtClass ctCl) throws NotFoundException, CannotCompileException {
        if (logging_enabled) System.out.println("INST: " + ctCl.getName());
        if (!ctCl.getName().equals("java.lang.invoke.InnerClassLambdaMetafactory"))
            throw new CannotCompileException(ctCl.getName() + " not supported");
        //private Class<?> spinInnerClass() throws LambdaConversionException {
        CtMethod m = ctCl.getMethod("spinInnerClass", "()Ljava/lang/Class;");
        m.instrument(new ExprEditor() {

            public void edit(MethodCall m) throws CannotCompileException {
                //UNSAFE.defineAnonymousClass(targetClass, classBytes, null);
                if (m.getMethodName().equals("defineAnonymousClass")) {
                    m.replace("$2 = " + Agent.class.getName() + ".instLambda($1,$2);" +
                            "$_ = $proceed($$); ");
                }
            }

        });
    }

    private static Agent agentInstance = null;
    private static boolean entered = false; //for recursion breaking, lambdas that are accessed within the framework are ignored.
    //TODO verify that thread independent is enough, or thread local is needed
    // intuition why this is enough: If two threads simultaneously bootstrap a lambda (on first invocation) and generate the functional interface implementation, this leads to
    // multiple class definitions and multiple executions of the same code generation, so this can be hardly happening.

    public static byte[] instLambda(Class<?> targetClass, byte[] classBytes) throws NotFoundException, IOException, CannotCompileException, JavassistBuildException {
        if (entered) return classBytes;
        try {
            entered = true;
            if (agentInstance == null) agentInstance = new Agent();
            String name = Util.getClassNameFromBytes(new ByteArrayInputStream(classBytes));
            //TODO move this filtering; use constatnt
            if (isExcluded(name)) return classBytes;
            ClassPool cp = agentInstance.getClassPool();
            cp.insertClassPath(new ByteArrayClassPath(name, classBytes));
            CtClass ctCl = cp.get(name);
            agentInstance.transformClass(ctCl);
            return ctCl.toBytecode();
        } finally {
            entered = false;
        }
    }

    public ClassPool getClassPool() throws NotFoundException {
        if (cp == null) {
            cp = ClassPool.getDefault();
            cp.insertClassPath(new LoaderClassPath(this.getClass().getClassLoader()));
        }
        return cp;
    }

    private static boolean dynamicallyTransform(String clName) {
        return (true || isIncluded(clName)) && !isExcluded(clName) && !isStaticallyInstrumented(clName);
    }

    //TODO can be optimized (binary search)
    private static boolean isExcluded(String name) {
        String nameDot = name.replace('/', '.');
        for (int i = 0; i < EXCLUDES.length; i++) {
            if (nameDot.startsWith(EXCLUDES[i])) return true;
        }
        return false;
    }

    private static boolean isStaticallyInstrumented(String name) {
        String nameDot = name.replace('/', '.');
        for (int i = 0; i < STATIC_INSTRUMENT.length; i++) {
            if (nameDot.startsWith(STATIC_INSTRUMENT[i])) return true;
        }
        return false;
    }

    private static boolean isIncluded(String name) {
        String nameDot = name.replace('/', '.');
        for (int i = 0; i < INCLUDES.length; i++) {
            if (nameDot.startsWith(INCLUDES[i])) return true;
        }
        return false;
    }

}
