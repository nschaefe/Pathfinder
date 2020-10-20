package boundarydetection.tracker.tasks;

import boundarydetection.tracker.AccessTracker;

public class Tasks {

    private static volatile ThreadLocal<Task> task;
    private static volatile ThreadLocal<Task> pausedTask;
    private static volatile ThreadLocal<Integer> pausedTaskCounter;

    private static volatile boolean b = false;

    private static void init() {
        if (b) return;
        synchronized (Tasks.class) {
            if (!b) {
                if (task == null) task = new ThreadLocal<>();
                if (pausedTask == null) {
                    pausedTask = new ThreadLocal<>();
                    pausedTaskCounter = new ThreadLocal<>();
                }
                b = true;
            }
        }
    }

    public static void startTask() {
        init();
        task.set(Task.createTask());
    }

    public static void startTask(String tag) {
        init();
        task.set(Task.createTask(tag));
    }

    public static void inheritTask(Task parent) {
        init();
        Task t = new Task(parent, false);
        t.setAutoInheritanceCount(parent.getAutoInheritanceCount() + 1);
        t.setWriteCapability(false);
        t.setParentTask(parent);
        task.set(t);
    }

    public static void stopTask() {
        init();
        if (task.get() != null) {
            task.get().stop();
        }
        task.remove();
    }

    public static boolean hasTask() {
        init();
        boolean b = task.get() != null;
        return b;
    }

    public static Task getTask() {
        init();
        return task.get();
    }

    public static void pauseTask() {
        // This approach uses a stack like counter to support subsequent pause and resume calls (e.g. pause, pause, resume, resume)
        // we only remember the task state of the really first call of pause and the bring the task back on the corresponding resume call
        // subsequent pauseTask and resumeTask calls will be just ignored.
        // We do not use a stack to remember all task states as they stack up and pop them on resume,
        // because of less dependencies and no recursion over java.util.Stack to AccessTracker.
        // I also experienced classloader deadlocks. The approach is also faster and is sufficient for the use cases we want to support.
        init();
        if (pausedTaskCounter.get() == null) pausedTaskCounter.set(0);

        int c = pausedTaskCounter.get();
        if (c == 0) {
            pausedTask.set(task.get());
            task.remove();
        }
        pausedTaskCounter.set(c + 1);

    }

    public static void resumeTask() {
        int c = pausedTaskCounter.get();
        c--;
        pausedTaskCounter.set(c);
        if (c == 0) {
            task.set(pausedTask.get());
        }
    }

    public static void join(Task t) throws TaskCollisionException {
        join(t, null);
    }

    public static void join(Task t, String tag) throws TaskCollisionException {
        if (t == null) return;
        init();
        if (!Tasks.hasTask()) {
            Task nt = new Task(t, true);
            nt.setWriteCapability(true);
            nt.addJoiner(t); // add to joiners
            nt.newSubTraceID();
            if (tag != null) nt.setTag(tag);
            task.set(nt);

        } else if (Tasks.getTask().getTraceID().equals(t.getTraceID())) {
            //normal join
            // Current task and task to be joined originated from the same task id
            // their parents does not need to be the same object but have the same task id
            Tasks.getTask().addJoiner(t);
        } else {
            throw new TaskCollisionException("Collision between present:" + Tasks.getTask().getTraceID() + " and to join:" + t.getTraceID());
            //collision
            // we have a current task running and there is another task to join.
            // this is inproper use. discard needed.
        }
    }

    public static void discard() {
        Tasks.stopTask();
    }

    public static Task fork() {
        if (!Tasks.hasTask()) return null;
        init();
        Task parent = Tasks.getTask();
        Task t = new Task(parent, false); //copy the task
        // we inherit joiners. This allows to detect transitive joins.
        // let a,b,c be threads; if context prop a->b->c and c reads from a, c will have a as joiner.
        // however this hides the case of an explicit join of a to c.
        // So we will ignore detections that are proved to be covered by a trace but might appear in the wrong branch
        t.setParentTask(parent);
        return t;
    }

    public static String serialize() {
        if (!Tasks.hasTask()) return null;
        return Tasks.getTask().serialize();
    }

    public static Task deserialize(String s) {
        if (s == null) return null;
        return Task.deserialize(s);
    }
}
