package boundarydetection.tracker;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

public class Util {


    public static int getApproxMemoryAddress(Object o) {
        return System.identityHashCode(o);
    }

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


    private static String process;

    public static String getProcessDesc() {
        if (process == null) {
            String process = ManagementFactory.getRuntimeMXBean().getName();
        }
        return process;
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

    private static int getIndexAfter(StackTraceElement[] trace, int start, String classPrefix) {
        for (int i = start; i < trace.length; i++) {
            if (!trace[i].getClassName().startsWith(classPrefix)) return i;
        }
        return trace.length;
    }

    public static String toString(StackTraceElement[] trace, String classPrefix, int end) {
        int start = getIndexAfter(trace, 1, classPrefix);
        return toString(trace, start, end);
    }

    public static String toString(StackTraceElement[] trace, int start, int end) {
        if (start < 0) throw new IllegalArgumentException("start must be >= 0");

        StringBuilder s = new StringBuilder();
        for (int i = start; i < trace.length && i < end; i++) {
            StackTraceElement el = trace[i];
            s.append(el);
            s.append(System.lineSeparator());
        }
        return s.toString();
    }

    public static String toString(StackTraceElement[] trace) {
        return toString(trace, 0, Integer.MAX_VALUE);
    }
    public static String toString(StackTraceElement[] trace,int end) {
        return toString(trace, 0, end);
    }

}
