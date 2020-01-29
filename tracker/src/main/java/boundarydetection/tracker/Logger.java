package boundarydetection.tracker;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Logger {

    private static Logger logger;

    public static Logger getLogger() {
        if (logger == null) {
            throw new IllegalStateException("logger not inited");
        }
        return logger;
    }

    public static void initLogger(String path) {
        logger = new Logger(path);
    }

    private Path path;

    public Logger(String path) {
        String name = "tracker_report";
        //"_" + Util.getProcessID() + "_" + Util.getProcessName() + "_" + Util.getHost();
        this.path = Paths.get(path, name);
    }

    //TODO speed this up
    public void log(String mess) {
        try (OutputStream fos = getOutputStream()) {
            mess = Util.getCurrentTime() + "_" + mess;
            fos.write(mess.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private OutputStream getOutputStream() throws FileNotFoundException {
        return new FileOutputStream(path.toFile(),true);
    }

}
