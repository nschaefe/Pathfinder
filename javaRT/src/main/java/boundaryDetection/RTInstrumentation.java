package boundaryDetection;

import boundarydetection.agent.Agent;
import javassist.*;

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

    public static void main(String[] args) {
        try {
            transformRT();
        } catch (Exception e) {
            System.err.println("ABORTED");
            e.printStackTrace();
        }
    }

    //TODO is it ok to omit not instrumented classes (should result in fallback to actual rt)
    private static void transformRT() throws NotFoundException, IOException, CannotCompileException {
        ClassPool cp = new ClassPool();
        //"JAVA_HOME/jre/lib/rt.jar";
        String rtpath = "/opt/jdk8/jre/lib/rt.jar";
        String resultPath = "./jar_out";
        cp.insertClassPath(rtpath);
        //TODO not nice
        cp.insertClassPath("../tracker/target/tracker-0.1-SNAPSHOT.jar");
        // DO NOT INCLUDE ANYTHING MORE THAT COULD BE USED INSTEAD OF WHAT IS IN THE RT
        Agent a = new Agent();
        for (String clName : readJarFiles(Paths.get(rtpath))) {
            CtClass cl = null;
            try {
                cl = cp.get(clName.replace("/", "."));
                //TODO filtering
                if (clName.startsWith("java/util") || clName.startsWith("java/io")) a.applyTransformations(cl);
                if (clName.startsWith("java/lang/ClassLoader")) a.instClassLoader(cl);
            } catch (Exception e) {
                // if instrumentation fails, we skip the class, results in fallback to actual rt at runtime
                System.err.println("ERROR but continue");
                e.printStackTrace();
                cl = cp.get(clName.replace("/", "."));
            } finally {
                cl.writeFile(resultPath);
            }

        }
        writeJar(resultPath, "./rt_inst.jar", readJarManifest(Paths.get(rtpath)));
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
