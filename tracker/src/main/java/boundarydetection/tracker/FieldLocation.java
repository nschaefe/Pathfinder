package  boundarydetection.tracker;

import java.lang.reflect.Type;

public class FieldLocation  extends AbstractFieldLocation {

    private Object parent;

    public FieldLocation(String location, Type type, Object parent) {
        super(location,type);
        this.parent = parent;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + System.identityHashCode(parent);
        hash = hash * 31 + getLocation().hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldLocation)) return false;
        FieldLocation other = (FieldLocation) o;
        return other.getLocation().equals(getLocation()) &&
                other.getType().equals(getType()) &&
                other.parent == parent;


    }
}
