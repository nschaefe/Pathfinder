package testcases;

import boundarydetection.tracker.AccessTracker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestPauseResume {

    @Test
    public void testPauseResume(){
        AccessTracker.startTask();
        Assertions.assertTrue(AccessTracker.hasTask());

        AccessTracker.pauseTask();
        Assertions.assertFalse(AccessTracker.hasTask());
        AccessTracker.pauseTask();
        Assertions.assertFalse(AccessTracker.hasTask());
        AccessTracker.pauseTask();
        Assertions.assertFalse(AccessTracker.hasTask());

        AccessTracker.resumeTask();
        Assertions.assertFalse(AccessTracker.hasTask());
        AccessTracker.resumeTask();
        Assertions.assertFalse(AccessTracker.hasTask());
        AccessTracker.resumeTask();
        Assertions.assertTrue(AccessTracker.hasTask());
    }

}
