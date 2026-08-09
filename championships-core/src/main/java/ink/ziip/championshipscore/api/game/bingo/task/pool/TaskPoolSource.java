package ink.ziip.championshipscore.api.game.bingo.task.pool;

/**
 * Static holder for the active task catalog. The bingo manager installs the selected card pool at
 * startup.
 */
public final class TaskPoolSource {
    private static volatile TaskPoolSpec active;
    private static volatile String activeName = "default";

    private TaskPoolSource() {
    }

    public static void set(TaskPoolSpec spec) {
        active = spec;
    }

    public static void set(TaskPoolSpec spec, String name) {
        active = spec;
        setName(name);
    }

    public static void setName(String name) {
        activeName = (name == null || name.isBlank()) ? "default" : name;
    }

    public static String activeName() {
        return activeName;
    }

    public static TaskPool pool() {
        TaskPoolSpec spec = active;
        return spec != null ? spec.toPool() : new TaskPoolSpec(java.util.List.of()).toPool();
    }
}
