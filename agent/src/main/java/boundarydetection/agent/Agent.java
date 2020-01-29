package boundarydetection.agent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

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
            "boundarydetection.tracker",

    };

    static final String[] INCLUDES = new String[]{"client/Client",
            "java/util/ArrayDeque",
            //"java/util/AbstractCollection",

            "java/util/ArrayList",
            "java/util/LinkedList",
            //"java/util/AbstractList",
            //"java/util/AbstractCollection",

            "java/util/concurrent/ArrayBlockingQueue"};
//            "java/util/concurrent/BlockingQueue",
//            "java/util/AbstractQueue",
//            "java/util/AbstractCollection"};


    public static void premain(final String agentArgument, final Instrumentation instrumentation) {
        instrumentation.addTransformer(new Agent(), true);

        // call for classes where agent is dependent on or that are used while bootstrapping
        for (Class c : instrumentation.getAllLoadedClasses()) {
            if (!isExcluded(c.getName())) {
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

//        for (int i = 0; i < EXCLUDES.length; i++) {
//            if (className.startsWith(EXCLUDES[i])) {
//                return bytes;
//            }
//        }

        // for debugging, isIncluded will disappear
        if (isIncluded(className)) {
            try {
                return transformClass(className, clazz, bytes);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return bytes;
    }

    private byte[] transformClass(final String name, final Class cl, byte[] b) throws CannotCompileException, NotFoundException, IOException {
        System.out.println("INST: " + name);
        ClassPool cp = ClassPool.getDefault();
        CtClass ctCl = cp.get(name.replace('/', '.'));
        CtClass tracker = cp.get("boundarydetection.tracker.AccessTracker");

        CodeConverter conv = new CodeConverter();

//      for (CtField f : ctCl.getDeclaredFields()) {
//          conv.replaceFieldRead(f, agentCl, "readField");
//      }

        conv.replaceArrayAccess(tracker, new CodeConverter.DefaultArrayAccessReplacementMethodNames());
        ctCl.instrument(conv);
        ctCl.instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess f) throws CannotCompileException {
//                $0	The object containing the field accessed by the expression. This is not equivalent to this.
//                this represents the object that the method including the expression is invoked on.
//                $0 is null if the field is static.
//
//
//                $1	The value that would be stored in the field if the expression is write access.
//                        Otherwise, $1 is not available.
//
//                        $_	The resulting value of the field access if the expression is read access.
//                        Otherwise, the value stored in $_ is discarded.
//
//                $r	The type of the field if the expression is read access.
//                        Otherwise, $r is void.
//
//                        $class    	A java.lang.Class object representing the class declaring the field.
//                $type	A java.lang.Class object representing the field type.
//                $proceed    	The name of a virtual method executing the original field access. .
//

                String ss = f.getSignature();
                String locationAsString = '"' + f.getClassName() + "." + f.getFieldName() + '"';
                if (ss.startsWith("L")) {
                    if (f.isReader()) {
                        f.replace(
                                "$_ = $proceed($$); " +
                                        "boundarydetection.tracker.AccessTracker.readObject($_, $0," + locationAsString + "); ");

                        // for value manipulation
                        // "$_ = (" + type + ")boundarydetection.tracker.AccessTracker.readObject($proceed($$), $0,\"" + ss + "\"); ";
                    } else {
                        f.replace("$proceed($$); " +
                                "boundarydetection.tracker.AccessTracker.writeObject($1, $0," + locationAsString + "); ");
                    }

                }
            }

        });


        ctCl.debugWriteFile();
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
            if (name.startsWith(INCLUDES[i])) return true;
        }
        return false;
    }


}
