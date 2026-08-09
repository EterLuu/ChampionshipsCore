package ink.ziip.championshipscore.api.game.bingo.task.pool;

import ink.ziip.championshipscore.api.game.bingo.task.TaskData;

import java.util.Objects;

/**
 * One catalog entry: a task objective, its difficulty, and an optional {@code category} that groups
 * sibling entries (e.g. all netherite-related items). The generator picks at most one task per
 * category per card so a small family can't crowd out everything else; {@code null} means "this entry
 * stands alone" and competes individually.
 */
public record TaskPoolEntry(TaskData task, Difficulty difficulty, String category) {
    public TaskPoolEntry {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(difficulty, "difficulty");
        if (category != null && category.isBlank()) {
            category = null;
        }
    }

    public TaskPoolEntry(TaskData task, Difficulty difficulty) {
        this(task, difficulty, null);
    }
}
