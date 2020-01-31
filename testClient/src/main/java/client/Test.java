package client;

public class Test {

    public static Integer y;
    private Integer x;

    public Test() {

    }

    public void write() {
        x = 1;
    }


    public void read() {
        int b = 1 + x;
    }

    public void static_write() {
        y = 1;
    }


    public void static_read() {
        int b = 1 + y;
    }

    public void call() {
        mm("infos");
    }

    public static void mm(String s) {

    }

}
