package boundarydetection.tracker;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Objects;

public class ArrayFieldLocation extends AbstractFieldLocation {

    private int index;
    private WeakReference ref;

    private static HashMap<String, String> arrayLocations = null;

    public synchronized static void registerLocation(Object array, String location) {
        if (arrayLocations == null) arrayLocations = new HashMap<>(1000); //TODO
        arrayLocations.put("" + array.getClass() + System.identityHashCode(array), location);
    }

    private synchronized static String getLocation(Object array) {
        String s = arrayLocations.get("" + array.getClass() + System.identityHashCode(array));
        if (s == null) return "unknown";
        else return s;
    }


    public ArrayFieldLocation(Class type, Object ref, int index) {
        //REMARK currently we do not support locations, because we do not need it for now,
        // we are rather interested in locations when we look at arrays as fields not the index access
        this("no location " + System.identityHashCode(ref), type, ref, index);
    }

    public ArrayFieldLocation(String location, Class type, Object ref, int index) {
        super(location, type);
        this.ref = new WeakReference<>(ref);
        this.index = index;
    }

    public Object getRef() {
        return ref.get();
    }

    @Override
    public int hashCode() {
        // getRef() returns null if object was deleted.
        // After this happened we consider the FieldLocation as archived and if it can be found in a data structure that uses
        // hash & equals paradigm is UNDEFINED. So finding something via hash after it was archived is not supported.
        // We use getRef in hash because it significantly improves the hash quality (and so performance). It is a perfect hash function if used.
        // For more information see voice record.
        int hash = 17;
        hash = hash * 31 + super.hashCode();
        hash = hash * 31 + System.identityHashCode(getRef());
        hash = hash * 31 + index;
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayFieldLocation)) return false;
        ArrayFieldLocation other = (ArrayFieldLocation) o;

        return Objects.equals(other.getLocation(), getLocation()) &&
                other.getType().equals(getType()) &&
                other.getRef() == getRef() &&
                other.index == index;

    }

    //TODO hold ref for logging (weak can make it null)
    @Override
    public void toJSON(JsonGenerator g) throws IOException {
        g.writeStringField("location", getLocation(getRef()));
        g.writeStringField("field_object_type", getType().toString());
        g.writeNumberField("reference", System.identityHashCode(getRef()));
        g.writeNumberField("index", index);
    }

}
