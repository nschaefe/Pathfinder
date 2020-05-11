package boundarydetection.tracker.tasks;

import boundarydetection.tracker.AbstractFieldLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

/**
 * Immutable task metadata
 */
public class Task {

    private Task parentTask; // null by default. Can be set to bind to another task

    private String taskID;
    private int inheritanceCount;

    private String eventIdPrefix;
    private int eventIDSeqNum;

    private Collection<String> parentEventIDs;

    private int serial;
    private int eventCounter;

    private Collection<AbstractFieldLocation> taskInheritanceLocation;

    private static int globalTaskCounter = 1;

    private boolean stopped;

    public Task(Task t) {
        this(t, t.inheritanceCount);
    }

    public Task(Task t, int inheritanceCount) {
        //copy construc.
        this.taskID = t.taskID;
        this.inheritanceCount = inheritanceCount;
        this.serial = t.serial;
        this.eventIdPrefix = t.eventIdPrefix;
        this.eventIDSeqNum = t.eventIDSeqNum;
        this.parentEventIDs = new ArrayList<>(t.parentEventIDs);
        this.eventCounter = t.eventCounter;
        // shallow copy is enough here, bc field locations are immutable and global by nature.
        // There can be exist several instances of a location. But they describe the same thing.
        // If we operate via equals, just knowing one instance is safe.
        this.taskInheritanceLocation = new HashSet<>(t.taskInheritanceLocation);
        this.parentTask = t.parentTask;
        this.stopped = t.stopped;
    }

    public Task(String taskID, int inheritanceCount) {
        this.taskID = taskID;
        this.inheritanceCount = inheritanceCount;
        this.parentEventIDs = new ArrayList<>();
        this.eventIdPrefix = null;
        this.eventIDSeqNum = 0;
        this.eventCounter = 0;
        this.taskInheritanceLocation = new HashSet<>();
        this.serial = globalTaskCounter++;
        this.parentTask = null;
        this.stopped = false;
    }

    public void setParentTask(Task parentTask) {
        this.parentTask = parentTask;
    }

    public Task getParentTask() {
        return parentTask;
    }

    public void stop() {
        stopped = true;
    }

    public boolean isAlive() {
        return !stopped;
    }

    public int getInheritanceCount() {
        return inheritanceCount;
    }

    public String getTaskID() {
        return taskID;
    }

    public void addInheritanceLocation(AbstractFieldLocation f) {
        taskInheritanceLocation.add(f);
    }

    public boolean hasInheritanceLocation(AbstractFieldLocation f) {
        return taskInheritanceLocation.contains(f);
    }

    public int getSerial() {
        return serial;
    }

    //--------------
    public void addAsParentEventID(String id) {
        if (hasEventID())
            parentEventIDs.add(id);
        else {
            eventIdPrefix = id;
            incrementEventID();
        }
    }

    public int getEventCounter() {
        return eventCounter + 1;
    }

    public void resetEventCounter() {
        eventCounter = 0;
    }

    public boolean hasEventID() {
        return eventIdPrefix != null;
    }

    public void incrementEventID() {
        parentEventIDs.clear();
        parentEventIDs.add(getEventID());
        eventIDSeqNum++;
        eventCounter++;
    }

    public String getEventID() {
        return eventIdPrefix + (eventIDSeqNum > 0 ? "_" + eventIDSeqNum : "");
    }

    public Collection<String> getParentEventIDs() {
        return parentEventIDs;
    }
    //--------------

    private static String getNewTaskID() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    public static Task createTask() {
        return new Task(getNewTaskID(), 0);
    }


}
