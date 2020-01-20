package boundarydetection.tracker;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

public class Util {


    public static String getCurrentTime() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        return formatter.format(date);
    }

    private static String processName;

    public static String getProcessName() {
        if (processName == null) {
            Class<?> mainClass = getMainClass();
            if (mainClass == null)
                return "";
            else
                processName = mainClass.getSimpleName();
        }
        return processName;
        // return "procName";
    }

    private static Class<?> MainClass;

    public static Class<?> getMainClass() {
        if (MainClass == null) {
            Collection<StackTraceElement[]> stacks = Thread.getAllStackTraces().values();
            for (StackTraceElement[] currStack : stacks) {
                if (currStack.length == 0)
                    continue;
                StackTraceElement lastElem = currStack[currStack.length - 1];
                if (lastElem.getMethodName().equals("main")) {
                    try {
                        String mainClassName = lastElem.getClassName();
                        MainClass = Class.forName(mainClassName);
                    } catch (ClassNotFoundException e) {
                        // bad class name in line containing main?!
                        // shouldn't happen
                        e.printStackTrace();
                    }
                }
            }
        }
        return MainClass;
    }


    private static Integer processID;

    public static int getProcessID() {
        if (processID == null) {
            String procname = ManagementFactory.getRuntimeMXBean().getName();
            processID = Integer.parseInt(procname.substring(0, procname.indexOf('@')));
        }
        return processID;
    }

    private static String host;

    public static String getHost() {
        if (host == null) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                host = "unknown";
            }
        }
        return host;
    }

}
