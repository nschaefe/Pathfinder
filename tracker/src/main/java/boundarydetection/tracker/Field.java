package  boundarydetection.tracker;

import java.lang.reflect.Type;

public class Field implements IField {

    private String location;
    private Type type;
    private Object parent;


    public Field(String location, Type type, Object parent) {
        this.location = location;
        this.type = type;
        this.parent = parent;

    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + System.identityHashCode(parent);
        hash = hash * 31 + location.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Field)) return false;
        Field other = (Field) o;
        return other.location.equals(location) &&
                other.type.equals(type) &&
                other.parent == parent;


    }
}
