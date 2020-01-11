package boundarydetection.agent;

import java.lang.reflect.Type;
import java.util.Objects;

public class ArrayField implements IField {

    private String location;
    private Class type;
    private Object ref;
    private int index;


    public ArrayField(Class type, Object ref, int index) {
        this(null, type, ref, index);
    }

    public ArrayField(String location, Class type, Object ref, int index) {
        this.location = location;
        this.type = type;
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
        if (!(o instanceof ArrayField)) return false;
        ArrayField other = (ArrayField) o;

        return  Objects.equals(other.location, location) &&
                other.type.equals(type) &&
                other.ref == ref &&
                other.index == index;

    }
}
