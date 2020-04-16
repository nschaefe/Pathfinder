package boundarydetection.tracker;

import boundarydetection.tracker.util.Util;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.lang.reflect.Type;

public final class FieldLocation extends AbstractFieldLocation {

    private int parent;

    public FieldLocation(String location, Type type, Object parent) {
        super(location, type);
        // theoretically there can be a collision on the parent reference but this requires the existence of many million
        // of objects of the same type, what is unlikely especially for a test setup (where the tool is used)
        this.parent = Util.getApproxMemoryAddress(parent);
    }

    public int getParentRef() {
        return parent;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + getLocation().hashCode();
        hash = hash * 31 + getParentRef();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldLocation)) return false;
        FieldLocation other = (FieldLocation) o;
        return other.getLocation().equals(getLocation()) &&
                other.getParentRef() == getParentRef();
    }

    @Override
    public String getUniqueIdentifier() {
        return getLocation()+'_'+getParentRef();
    }

    @Override
    public void toJSON(JsonGenerator g) throws IOException {
        super.toJSON(g);
        g.writeNumberField("parent", getParentRef());
    }
}
