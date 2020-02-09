package  boundarydetection.tracker;

import java.util.Objects;

public class ArrayFieldLocation extends AbstractFieldLocation {

    private Object ref;
    private int index;


    public ArrayFieldLocation(Class type, Object ref, int index) {
        this(null, type, ref, index);
    }

    public ArrayFieldLocation(String location, Class type, Object ref, int index) {
        super(location,type);
        this.ref = ref;
        this.index = index;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + System.identityHashCode(ref);
        hash = hash * 31 + index;
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayFieldLocation)) return false;
        ArrayFieldLocation other = (ArrayFieldLocation) o;

        return  Objects.equals(other.getLocation(), getLocation()) &&
                other.getType().equals(getType()) &&
                other.ref == ref &&
                other.index == index;

    }
}
