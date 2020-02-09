package boundarydetection.tracker;

import java.lang.reflect.Type;

public abstract class AbstractFieldLocation {

    private String location;
    private Type type;

    public AbstractFieldLocation(String location, Type type) {
        this.location = location;
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public Type getType() {
        return type;
    }

}
