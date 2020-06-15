package boundarydetection.instrumentation;

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
}
