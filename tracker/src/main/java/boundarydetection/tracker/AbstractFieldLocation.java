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


    public void toJSON(JsonGenerator g) throws IOException {
       g.writeStringField("location", location);
       g.writeStringField("field_object_type", type.toString());
    }

}
