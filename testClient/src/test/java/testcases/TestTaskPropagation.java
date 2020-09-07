package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestTaskPropagation {

    @Test
    public void testSerializeDeserialize() {
        AccessTracker.startTask();
        String traceID = AccessTracker.getTask().getTraceID();

        String s = AccessTracker.serialize();
        AccessTracker.stopTask();

        AccessTracker.join(AccessTracker.deserialize(s));

        Assertions.assertTrue(AccessTracker.hasTask());
        Assertions.assertEquals(traceID, AccessTracker.getTask().getTraceID());
    }

}
