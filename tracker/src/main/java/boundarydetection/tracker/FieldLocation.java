package boundarydetection.tracker;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;

public class FieldLocation extends AbstractFieldLocation {

    private WeakReference parent;

    public FieldLocation(String location, Type type, Object parent) {
        super(location, type);
        this.parent = new WeakReference<>(parent);
    }

    public Object getParent() {
        return parent.get();
    }

    @Override
    public int hashCode() {
        // getParent() returns null if object was deleted.
        // After this happened we consider the FieldLocation as archived and if it can be found in a data structure that uses
        // hash & equals paradigm is UNDEFINED. So finding something via hash after it was archived is not supported.
        // We use getParent in hash because it significantly improves the hash quality (and so performance). It is a perfect hash function if used.
        // For more information see voice record.
        int hash = 17;
        hash = hash * 31 + super.hashCode();
        hash = hash * 31 + System.identityHashCode(getParent());
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldLocation)) return false;
        FieldLocation other = (FieldLocation) o;
        return other.getLocation().equals(getLocation()) &&
                other.getType().equals(getType()) &&
                other.getParent() == getParent();
    }

    //TODO hold ref for logging (weak can make it null)
    @Override
    public void toJSON(JsonGenerator g) throws IOException {
        super.toJSON(g);
        g.writeNumberField("parent", System.identityHashCode(parent));
    }
}
