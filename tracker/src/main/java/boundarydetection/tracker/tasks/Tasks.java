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
        AccessTracker.startTracking();
        init();
        task.set(Task.createTask());
    }

    public static void inheritTask(Task parent) {
        init();
        Task t = new Task(parent);
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
        init();
        if (!Tasks.hasTask()) {
            task.set(new Task(t));
            Tasks.getTask().addJoiner(t); // add to joiners
            Tasks.getTask().newSubTraceID();

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
        Task t = new Task(parent);
        t.clearJoiner();
        t.setParentTask(parent);
        return t;
    }

}
