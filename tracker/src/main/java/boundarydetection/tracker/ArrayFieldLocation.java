package boundarydetection.tracker;

import boundarydetection.tracker.util.Util;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.HashMap;


//TODO naming
public final class ArrayFieldLocation extends AbstractFieldLocation {

    private int index;
    private int objectRef;

    private static HashMap<Integer, String> arrayLocations = null;

    public synchronized static void registerLocation(Object array, String location) {
        if (arrayLocations == null) arrayLocations = new HashMap<>(1000); //TODO
        arrayLocations.put(Util.getApproxMemoryAddress(array), location);
    }

    private synchronized static String getLocationByRef(int ref) {
        String s;
        if (arrayLocations == null) s = null;
        else s = arrayLocations.get(ref);

        if (s == null) return "unknown";
        else return s;
    }

//    private synchronized static String getLocation(Object array) {
//       return  getLocationByRef(Util.getApproxMemoryAddress(array));
//    }


    public ArrayFieldLocation(Class type, Object objectRef, int index) {
        //REMARK to infer the array location is not trivial. An array can be local or passed a long way before usage.
        // we infer the array location dynamically. When the location is known later getLocation() will return it.
        this("unknown", type, objectRef, index);
    }

    public ArrayFieldLocation(String location, Class type, Object objectRef, int index) {
        super(location, type);
        this.objectRef = Util.getApproxMemoryAddress(objectRef);
        this.index = index;
    }

    public int getArrayObjectReference() {
        return objectRef;
    }

    @Override
    public String getLocation() {
        return getLocationByRef(getArrayObjectReference());
    }

    //TODO use type to reduce collision prob for reference

    private int hash=0;

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = 17;
            hash = hash * 31 + index;
            hash = hash * 31 + getArrayObjectReference();
        }

        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayFieldLocation)) return false;
        ArrayFieldLocation other = (ArrayFieldLocation) o;

        return other.index == index && other.getArrayObjectReference() == getArrayObjectReference();
    }

    @Override
    public String getUniqueIdentifier() {
        return "" + this.getType() + '_' + getArrayObjectReference() + '_' + index;
    }

    @Override
    public void toJSON(JsonGenerator g) throws IOException {
        g.writeStringField("location", getLocation());
        g.writeStringField("field_object_type", getType().toString());
        g.writeNumberField("reference", getArrayObjectReference());
        g.writeNumberField("index", index);
    }

}
