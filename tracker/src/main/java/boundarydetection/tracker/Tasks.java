package boundarydetection.tracker;

public class Tasks {

    private static volatile ThreadLocal<Boolean> task;
    private static volatile ThreadLocal<Boolean> pausedTask;
    private static volatile ThreadLocal<Integer> pausedTaskCounter;


    private synchronized static void init() {
        if (task == null) task = new ThreadLocal<>();
        if (pausedTask == null) {
            pausedTask = new ThreadLocal<>();
            pausedTaskCounter = new ThreadLocal<>();
        }
    }

    static void startTask() {
        AccessTracker.startTracking();
        init();
        task.set(true);
    }

    static void stopTask() {
        init();
        task.set(false);
    }

    static boolean hasTask() {
        init();
        boolean b = task.get() != null && task.get();
        return b;
    }

    static void pauseTask() {
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

    static void resumeTask() {
        int c = pausedTaskCounter.get();
        c--;
        pausedTaskCounter.set(c);
        if (c == 0) {
            task.set(pausedTask.get());
        }
    }


}
