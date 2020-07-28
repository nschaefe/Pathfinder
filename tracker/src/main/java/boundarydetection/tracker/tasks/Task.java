package boundarydetection.tracker.tasks;

import boundarydetection.tracker.AbstractFieldLocation;

import java.util.*;

/**
 * Immutable task metadata
 */
public class Task {

    private Task parentTask; // null by default. Can be set to bind to another task
    private String traceID; // serial and trace id belong to high level
    private int serial;
    private String tag;

    private String subTraceID;
    private boolean stopped;
    private Set<Task> joiners;


    private String eventIdPrefix;
    private int eventIDSeqNum;
    private Collection<String> parentEventIDs;
    private int eventCounter;

    private Collection<AbstractFieldLocation> taskInheritanceLocation;
    private int autoInheritanceCount;

    private boolean writeCapability;

    private static int globalTaskCounter = 1;

    public Task(Task t) {
        if (t == null) throw new NullPointerException("Task to copy is null");
        //copy construc.
        this.traceID = t.traceID;
        this.subTraceID = t.subTraceID;
        this.autoInheritanceCount = t.autoInheritanceCount;
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
        this.joiners = new HashSet<>(t.joiners);
        this.writeCapability = t.writeCapability;
        this.tag = t.tag;
    }

    public Task(String traceID) {
        this(traceID,null);
    }

    public Task(String traceID, String tag) {
        this.traceID = traceID;
        this.subTraceID = traceID + "_" + Math.random();
        this.autoInheritanceCount = 0;
        this.parentEventIDs = new ArrayList<>();
        this.eventIdPrefix = null;
        this.eventIDSeqNum = 0;
        this.eventCounter = 0;
        this.taskInheritanceLocation = new HashSet<>();
        this.serial = globalTaskCounter++;
        this.parentTask = null;
        this.stopped = false;
        this.joiners = new HashSet<>();
        this.writeCapability = true;
        this.tag = tag;
    }

    public void addJoiner(Task task) {
        joiners.add(task);
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Task[] getJoiners() {
        return joiners.toArray(new Task[joiners.size()]);
    }

    public void clearJoiner() {
        joiners.clear();
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

    public int getAutoInheritanceCount() {
        return autoInheritanceCount;
    }

    void setAutoInheritanceCount(int autoInheritanceCount) {
        this.autoInheritanceCount = autoInheritanceCount;
    }

    public String getTraceID() {
        return traceID;
    }

    public void newSubTraceID() {
        String[] s = subTraceID.split("_");
        subTraceID = s[0] + "_" + (Double.parseDouble(s[1]) * 17 + Math.random());
    }

    public String getSubTraceID() {
        return this.subTraceID;
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

    public boolean hasWriteCapability() {
        return writeCapability;
    }

    public void setWriteCapability(boolean writeCapability) {
        this.writeCapability = writeCapability;
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
        return new Task(getNewTaskID());
    }

    public static Task createTask(String tag) {
        return new Task(getNewTaskID(), tag);
    }

}
