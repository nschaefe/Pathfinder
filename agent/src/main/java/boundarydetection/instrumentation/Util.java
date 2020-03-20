package boundarydetection.instrumentation;

public class Util {

    public static boolean isSingleObjectSignature(String s) {
          return !hasPrimitive(s) && !s.startsWith("[");
    }

    public static boolean isObjectArraySignature(String s) {
        return !hasPrimitive(s) && s.startsWith("[");
    }

    private static boolean hasPrimitive(String s) {
        return s.length() <= 2; // TODO check this
    }

}
