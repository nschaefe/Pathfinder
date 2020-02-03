package boundarydetection.instrumentation;

public class Util {

    public static boolean isSingleObjectSignature(String s) {
        if (s.length() <= 1) return false; //TODO
        return !s.startsWith("[");
    }

}
