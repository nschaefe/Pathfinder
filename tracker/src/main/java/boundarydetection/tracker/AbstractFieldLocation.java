package boundarydetection.tracker;

import java.io.IOException;
import java.lang.reflect.Type;
import com.fasterxml.jackson.core.JsonGenerator;

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

    @Override
    public int hashCode() {
        // we do not consider type here, since there can never be different types for the same location
        //does not improve the hash
        return location.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractFieldLocation)) return false;
        AbstractFieldLocation other = (AbstractFieldLocation) o;

        return other.location.equals(location) &&
                other.type.equals(type);
    }


    public void toJSON(JsonGenerator g) throws IOException {
        g.writeStringField("location", location);
        g.writeStringField("field_object_type", type.toString());
    }

}
