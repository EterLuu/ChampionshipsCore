package ink.ziip.championshipscore.api.game.bingo.task.pool;

import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticHandle;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Immutable catalog of {@link TaskPoolEntry}. Owns content definition (via {@link Builder}), filtering
 * (via {@link #filter}), and exposure of entries for the generator to sample. It never samples or
 * randomises – that lives in {@code TaskGenerator}.
 */
public final class TaskPool {
    private final List<TaskPoolEntry> entries;

    private TaskPool(List<TaskPoolEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<TaskPoolEntry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /**
     * A new pool keeping only entries whose task type is in {@code includedTypes} and whose
     * dimension is not in {@code disabledDimensions}.
     */
    public TaskPool filter(Set<TaskData.TaskType> includedTypes, Set<Dimension> disabledDimensions) {
        List<TaskPoolEntry> kept = new ArrayList<>(entries.size());
        for (TaskPoolEntry entry : entries) {
            if (!includedTypes.contains(entry.task().getType())) {
                continue;
            }
            if (entry.task().inAnyDimension(disabledDimensions)) {
                continue;
            }
            kept.add(entry);
        }
        return new TaskPool(kept);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent catalog builder. Each {@code add*} call appends one entry; advancement entries are
     * resolved against the running server and silently skipped when the advancement is absent.
     */
    public static final class Builder {
        private final List<TaskPoolEntry> entries = new ArrayList<>();

        // ── ITEM ──
        public Builder item(Material material, Difficulty difficulty) {
            return item(material, 1, difficulty, Dimension.OVERWORLD, null);
        }

        public Builder item(Material material, Difficulty difficulty, Dimension dimension) {
            return item(material, 1, difficulty, dimension, null);
        }

        public Builder item(Material material, int count, Difficulty difficulty,
                            Dimension dimension, String category) {
            entries.add(new TaskPoolEntry(new ItemTask(material, count, dimension), difficulty, category));
            return this;
        }

        // ── ONE_OF (collect any one of a set) ──
        public Builder oneOf(Set<Material> items, Material display, String name, int count,
                             Difficulty difficulty, Dimension dimension, String category) {
            entries.add(new TaskPoolEntry(
                    new OneOfTask(items, display, name, count, dimension), difficulty, category));
            return this;
        }

        // ── ADVANCEMENT ──
        public Builder advancement(String path, Difficulty difficulty) {
            Dimension dim = path.startsWith("nether/") ? Dimension.NETHER
                    : path.startsWith("end/") ? Dimension.THE_END
                    : Dimension.OVERWORLD;
            return advancement(path, difficulty, dim, null);
        }

        public Builder advancement(String path, Difficulty difficulty, Dimension dimension, String category) {
            Advancement advancement = Bukkit.getAdvancement(NamespacedKey.minecraft(path));
            if (advancement == null) {
                return this;
            }
            entries.add(new TaskPoolEntry(new AdvancementTask(advancement, dimension), difficulty, category));
            return this;
        }

        // ── MINE / CRAFT / KILL / STAT ──
        public Builder mineBlock(Material block, int count, Difficulty difficulty,
                                 Dimension dimension, String category) {
            entries.add(new TaskPoolEntry(
                    new StatisticTask(new StatisticHandle(Statistic.MINE_BLOCK, block), count, dimension),
                    difficulty, category));
            return this;
        }

        public Builder craft(Material item, int count, Difficulty difficulty,
                             Dimension dimension, String category) {
            entries.add(new TaskPoolEntry(
                    new StatisticTask(new StatisticHandle(Statistic.CRAFT_ITEM, item), count, dimension),
                    difficulty, category));
            return this;
        }

        public Builder kill(EntityType entity, int count, Difficulty difficulty,
                            Dimension dimension, String category) {
            entries.add(new TaskPoolEntry(
                    new StatisticTask(new StatisticHandle(Statistic.KILL_ENTITY, entity), count, dimension),
                    difficulty, category));
            return this;
        }

        public Builder stat(Statistic statistic, int count, Difficulty difficulty,
                            Dimension dimension, String category) {
            entries.add(new TaskPoolEntry(
                    new StatisticTask(new StatisticHandle(statistic), count, dimension),
                    difficulty, category));
            return this;
        }

        public TaskPool build() {
            return new TaskPool(entries);
        }
    }
}
