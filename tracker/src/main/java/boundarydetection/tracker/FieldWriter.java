package boundarydetection.tracker;

import boundarydetection.tracker.tasks.Task;

public class FieldWriter {

    private long threadID;
    private Task task;
    private long clock;
    private Throwable trace;


    public FieldWriter(long threadID, Task task, Throwable trace, long clock) {
        this.threadID = threadID;
        this.task = task;
        this.trace = trace;
        this.clock = clock;
    }


    public Task getTask() {
        return task;
    }

    public long getThreadID() {
        return threadID;
    }

    public StackTraceElement[] getStackTrace() {
        return trace.getStackTrace();
    }

    public long getClock() {
        return clock;
    }

}
