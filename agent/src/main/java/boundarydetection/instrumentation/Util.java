package boundarydetection.instrumentation;

import javassist.CtBehavior;
import javassist.Modifier;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Util {

    public static boolean isSingleObjectSignature(String s) {
        return !hasPrimitive(s) && !s.startsWith("[");
    }

    public static boolean isObjectSignature(String s) {
        return !isPrimitive(s);
    }


    public static boolean isArraySignature(String s) {
        return s.startsWith("[");
    }

    public static boolean isDoubleOrLong(String s) {
        return s.equals("D") || s.equals("J");
    }

    public static boolean isObjectArraySignature(String s) {
        return !hasPrimitive(s) && s.startsWith("[");
    }

    private static boolean hasPrimitive(String s) {
        return s.length() <= 2; // TODO check this
    }

    private static boolean isPrimitive(String s) {
        return s.length() == 1; // TODO check this
    }

    public static boolean isNative(CtBehavior method) {
        return Modifier.isNative(method.getModifiers());
    }

    public static boolean isAbstract(CtBehavior method) {
        return Modifier.isAbstract(method.getModifiers());
    }

    public static String getClassNameFromBytes(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(is);
        dis.readLong(); // skip header and class version
        int cpcnt = (dis.readShort() & 0xffff) - 1;
        int[] classes = new int[cpcnt];
        String[] strings = new String[cpcnt];
        for (int i = 0; i < cpcnt; i++) {
            int t = dis.read();
            if (t == 7) classes[i] = dis.readShort() & 0xffff;
            else if (t == 1) strings[i] = dis.readUTF();
            else if (t == 5 || t == 6) {
                dis.readLong();
                i++;
            } else if (t == 8) dis.readShort();
            else dis.readInt();
        }
        dis.readShort(); // skip access flags
        return strings[classes[(dis.readShort() & 0xffff) - 1] - 1].replace('/', '.');
    }
}
