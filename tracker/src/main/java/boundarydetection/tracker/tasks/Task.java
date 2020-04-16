package boundarydetection.tracker.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Immutable task metadata
 */
public class Task {

    private String taskID;
    private int inheritanceCount;

    private String eventIdPrefix;
    private int eventIDSeqNum;

    private Collection<String> parentEventIDs;

    public Task(Task t) {
        this(t, t.inheritanceCount);
    }

    public Task(Task t, int inheritanceCount) {
        //copy construc.
        this.taskID = t.taskID;
        this.inheritanceCount = inheritanceCount;
        eventIdPrefix = t.eventIdPrefix;
        eventIDSeqNum = t.eventIDSeqNum;
        parentEventIDs = new ArrayList<>(t.parentEventIDs);
    }

    public Task(String taskID, int inheritanceCount) {
        this.taskID = taskID;
        this.inheritanceCount = inheritanceCount;
        this.parentEventIDs = new ArrayList<>();
        this.eventIdPrefix = null;
        this.eventIDSeqNum = 0;
    }

    public int getInheritanceCount() {
        return inheritanceCount;
    }

    public String getTaskID() {
        return taskID;
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

    public boolean hasEventID() {
        return eventIdPrefix != null;
    }

    public void incrementEventID() {
        parentEventIDs.clear();
        parentEventIDs.add(getEventID());
        eventIDSeqNum++;
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
