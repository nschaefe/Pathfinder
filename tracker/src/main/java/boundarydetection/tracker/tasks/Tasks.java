package boundarydetection.tracker.tasks;

import boundarydetection.tracker.AccessTracker;

public class Tasks {

    private static volatile ThreadLocal<Task> task;
    private static volatile ThreadLocal<Task> pausedTask;
    private static volatile ThreadLocal<Integer> pausedTaskCounter;


    private synchronized static void init() {
        if (task == null) task = new ThreadLocal<>();
        if (pausedTask == null) {
            pausedTask = new ThreadLocal<>();
            pausedTaskCounter = new ThreadLocal<>();
        }
    }

    public static void startTask() {
        AccessTracker.startTracking();
        init();
        task.set(Task.createTask());
    }

    public static void startTask(Task t) {
        init();
        t = new Task(t, t.getInheritanceCount() + 1);
        task.set(t);
    }

    public static void stopTask() {
        init();
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


}
