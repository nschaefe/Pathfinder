package boundaryDetection;

import boundarydetection.agent.Agent;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.build.JavassistBuildException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class RTInstrumentation {

    private static boolean FALLBACK_ON_ERROR = false;

    public static void main(String[] args) {
        try {
            if (args.length < 2) throw new IllegalArgumentException("Not enough arguments");
            transformRT(args[0], args[1]);
        } catch (Exception e) {
            System.err.println("ABORTED");
            e.printStackTrace();
        }
    }

    private static void transformRT(String trackerPath, String baseDir) throws NotFoundException, IOException, CannotCompileException, JavassistBuildException {
        Agent a = new Agent();
        ClassPool cp = a.getClassPool();

        String javaHome = System.getenv("JAVA_HOME");
        String rtHome;
        if (javaHome == null || javaHome.isEmpty()) {
            System.out.println("JAVA_HOME not set");
            rtHome = baseDir;
        } else rtHome = javaHome + "/jre/lib";
        String rtpath = rtHome + "/rt.jar";
        System.out.println("Reading rt from " + rtpath);

        String resultPath = baseDir + "/jar_out";
        cp.insertClassPath(rtpath); // DO NOT INCLUDE ANYTHING MORE THAT CONTAINS JAVA RT CLASSES
        cp.insertClassPath(trackerPath);

        for (String clName : readJarFiles(Paths.get(rtpath))) {
            CtClass cl = null;
            try {
                cl = cp.get(clName.replace("/", "."));
                //TODO filtering at central point
                if (doInstrument(clName)) a.transformClass(cl);
                if (clName.equals("java/lang/ClassLoader")) a.instClassLoader(cl);
                if (clName.equals("java/lang/invoke/InnerClassLambdaMetafactory")) a.instLambdaMetaFactory(cl);
            } catch (Exception e) {
                if (FALLBACK_ON_ERROR) {
                    // if instrumentation fails, we skip the class, results in fallback to actual rt at runtime
                    System.err.println("ERROR in " + cl.getName() + ", continue");
                    e.printStackTrace();
                    cl.detach();
                    cl = cp.get(clName.replace("/", "."));
                } else{
                    System.err.println("ERROR in " + cl.getName() );
                    throw e;
                }
            } finally {
                cl.writeFile(resultPath);
            }

        }
        writeJar(resultPath, baseDir + "/rt_inst.jar", readJarManifest(Paths.get(rtpath)));
    }

    private static boolean doInstrument(String clName) {
        // Requirement for class C to be instrumented: There must be a sensible case where Thread A communicates with Thread B via fields in C as part of an actual ITC task and this is the primary communication channel.
        // This does not mean that we do not care about an object of this class to be transmitted to another thread but rather if the communication channel happens exclusively over fields of the objects
        // In the end its a about long-term (many state manipulations) objects vs short-term objects (effectively immutable or barely state manipulations). Without state changes there can not be an ITC.
        // Otherwise If there is no ITC tracking logic just causes overhead and if there is an ITC but its not at all related to any ITC task it just leads to false positives.
        // Counter examples:
        // java.math.* does only contain immutables.
        // java.time.Date very unlikely to serve as primary communication place.

//        https://docs.oracle.com/javase/8/docs/api/
//
//        applet- no!
//        awt - no!
//        beans - (yes) (to support bean handling (generators, encoder, decoder for serialization)
//        io - (yes) (streams)
//                lang - (yes) (only basic types,ref; reflection is just a runtime filed, method ref, rest is runtime related)
//        math - no!
//        net - (yes)
//        nio - (yes) buffers
//        rmi - (no) (inter JVM communication)
//        security - (no) (to realize security in an app, encryption, access control)
//        sql - (no) (just SQL Driver stuff)
//        text - (yes)
//        time - (yes)
//        util - yes


        // assuming no rmi, sql is used
        return (clName.startsWith("java/util") && !clName.startsWith("java/util/concurrent/locks")) ||
                clName.startsWith("java/nio") ||
                clName.startsWith("java/net") ||
                clName.startsWith("java/math") ||
                clName.startsWith("java/time") ||
                clName.startsWith("java/text") ||
                clName.startsWith("java/io") ||
                clName.startsWith("java/beans") ||
                clName.startsWith("java/security/spec") ||
                clName.startsWith("java/security/acl") ||
                clName.startsWith("java/security/interfaces") ||
                clName.startsWith("java/security/cert") ||
                isBasicType(clName);

    }

    private static boolean isBasicType(String name) {
        return name.startsWith("java/lang") &&
                (name.startsWith("java/lang/Long") ||
                        name.startsWith("java/lang/Integer") ||
                        name.startsWith("java/lang/Short") ||
                        name.startsWith("java/lang/Byte") ||
                        name.startsWith("java/lang/Double") ||
                        name.startsWith("java/lang/Float") ||
                        name.startsWith("java/lang/Number") ||
                        name.startsWith("java/lang/Character"));
// Strings are immutable and so cannot cause additional inter thread communications.
// Tracking the field containing the string is sufficient. We do this by tracking object fields.
// name.startsWith("java/lang/String"));

//TODO  name.startsWith("java/lang/Boolean") causes a SIGSEV. I doubt that this is easy to fix.
    }


    //TODO better: return a jarMetadata container with all required data
    private static Manifest readJarManifest(Path path) throws IOException {
        try (JarInputStream in = new JarInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            return in.getManifest();
        }
    }

    private static String[] readJarFiles(Path path) throws IOException {
        try (JarInputStream in = new JarInputStream(new BufferedInputStream(Files.newInputStream(path)))) {

            List<String> cls = new ArrayList<String>();
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;

                name = name.replaceAll(".class$", "");
                cls.add(name);

            }
            return cls.toArray(new String[cls.size()]);
        }
    }

    private static void writeJar(String inDir, String outPath, Manifest manifest) throws IOException {
        JarOutputStream target = new JarOutputStream(new FileOutputStream(outPath), manifest);
        for (File d : (new File(inDir)).listFiles()) {
            add(d, target, inDir + "/");
        }
        target.close();
    }

    private static void add(File source, JarOutputStream target, String basePath) throws IOException {
        BufferedInputStream in = null;
        try {
            String path = source.getPath();
            path = path.substring(basePath.length(), path.length());
            String name = path.replace("\\", "/");
            if (source.isDirectory()) {
                if (!name.isEmpty()) { //TODO empty??
                    if (!name.endsWith("/")) name += "/";
                    JarEntry entry = new JarEntry(name);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }
                for (File nestedFile : source.listFiles())
                    add(nestedFile, target, basePath);
                return;
            }

            JarEntry entry = new JarEntry(name);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));
            byte[] buffer = new byte[1024];
            while (true) {
                int count = in.read(buffer);
                if (count == -1)
                    break;
                target.write(buffer, 0, count);
            }
            target.closeEntry();
        } finally {
            if (in != null) in.close();
        }
    }

}
