package ink.ziip.championshipscore.api.game.bingo.task.pool;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.task.EventSubject;
import ink.ziip.championshipscore.api.game.bingo.task.EventTask;
import ink.ziip.championshipscore.api.game.bingo.util.Materials;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered, serialisable list of {@link PoolEntrySpec} — the configurable representation of the task
 * catalog. {@link #toPool()} turns these specs back into a live {@link TaskPool} by resolving
 * advancements, materials and entities against the running server, skipping anything invalid.
 */
public final class TaskPoolSpec {
    private final List<PoolEntrySpec> entries;

    public TaskPoolSpec(List<PoolEntrySpec> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<PoolEntrySpec> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Resolves every spec against the live server, skipping (with a warning) any unknown identifier. */
    public TaskPool toPool() {
        TaskPool.Builder b = TaskPool.builder();
        Tierlist tierlist = TierlistSource.active();
        for (PoolEntrySpec e : entries) {
            // Difficulty comes from the active tier list; objectives the list doesn't rank fall back to
            // the spec default (MEDIUM).
            Difficulty difficulty = tierlist.resolve(e.objectiveId()).orElse(e.difficulty());
            try {
                switch (e.kind()) {
                    case ITEM -> {
                        Material m = Material.valueOf(e.key());
                        if (!Materials.isCollectObjective(m)) {
                            ChampionshipsCore.getInstance().getLogger().warning(gameLog("不可获取的 ITEM=" + e.key() + "，已跳过"));
                        } else {
                            b.item(m, e.count(), difficulty, e.dimension(), e.category());
                        }
                    }
                    case ADVANCEMENT ->
                            b.advancement(e.key(), difficulty, e.dimension(), e.category());
                    case POTION -> {
                        // key is "<form>:<effect>"; both must be valid (effect must be a real PotionType).
                        String[] parts = e.key().split(":", 2);
                        ink.ziip.championshipscore.api.game.bingo.task.PotionTask.Form form =
                                parts.length == 2 ? ink.ziip.championshipscore.api.game.bingo.task.PotionTask.Form.parse(parts[0]) : null;
                        String effect = parts.length == 2 ? parts[1] : null;
                        boolean valid = effect != null;
                        if (valid) {
                            try {
                                org.bukkit.potion.PotionType.valueOf(effect.toUpperCase(java.util.Locale.ROOT));
                            } catch (IllegalArgumentException ex) {
                                valid = false;
                            }
                        }
                        if (form == null || !valid) {
                            ChampionshipsCore.getInstance().getLogger().warning(gameLog("无效药水任务=" + e.key() + "，已跳过"));
                        } else {
                            b.potion(form, effect, e.count(), difficulty, e.dimension(), e.category());
                        }
                    }
                    case MINE ->
                            b.mineBlock(Material.valueOf(e.key()), e.count(), difficulty, e.dimension(), e.category());
                    case CRAFT -> {
                        Material m = Material.valueOf(e.key());
                        if (!m.isItem()) {
                            ChampionshipsCore.getInstance().getLogger().warning(gameLog("非物品 CRAFT=" + e.key() + "，已跳过"));
                        } else {
                            b.craft(m, e.count(), difficulty, e.dimension(), e.category());
                        }
                    }
                    case KILL ->
                            b.kill(EntityType.valueOf(e.key()), e.count(), difficulty, e.dimension(), e.category());
                    case STAT ->
                            b.stat(Statistic.valueOf(e.key()), e.count(), difficulty, e.dimension(), e.category());
                    case ONE_OF -> {
                        java.util.LinkedHashSet<Material> items = new java.util.LinkedHashSet<>();
                        for (String m : e.members()) {
                            try {
                                Material mat = Material.valueOf(m);
                                if (Materials.isCollectObjective(mat)) items.add(mat);
                            } catch (IllegalArgumentException ignored) {
                                // skip unknown member, keep the rest of the set usable
                            }
                        }
                        if (items.isEmpty()) {
                            ChampionshipsCore.getInstance().getLogger().warning(gameLog("one_of 任务没有有效成员，已跳过"));
                        } else {
                            Material display = null;
                            if (e.key() != null && !e.key().isBlank()) {
                                try {
                                    Material d = Material.valueOf(e.key());
                                    if (d.isItem()) display = d;
                                }
                                catch (IllegalArgumentException ignored) { /* fall back to first member */ }
                            }
                            b.oneOf(items, display, e.name(), e.count(), difficulty, e.dimension(), e.category());
                        }
                    }
                    case ALL_OF -> {
                        java.util.LinkedHashSet<Material> items = new java.util.LinkedHashSet<>();
                        for (String m : e.members()) {
                            try {
                                Material mat = Material.valueOf(m);
                                if (Materials.isCollectObjective(mat)) items.add(mat);
                            } catch (IllegalArgumentException ignored) {
                                // skip unknown member, keep the rest of the set usable
                            }
                        }
                        if (items.isEmpty()) {
                            ChampionshipsCore.getInstance().getLogger().warning(gameLog("all_of 任务没有有效成员，已跳过"));
                        } else {
                            Material display = null;
                            if (e.key() != null && !e.key().isBlank()) {
                                try {
                                    Material d = Material.valueOf(e.key());
                                    // The representative icon may sit outside the member set (e.g. the
                                    // mushroom family), unlike one_of.
                                    display = d;
                                }
                                catch (IllegalArgumentException ignored) { /* fall back to first member */ }
                            }
                            b.allOf(items, display, e.name(), e.count(), difficulty, e.dimension(), e.category());
                        }
                    }
                    case EVENT -> {
                        // key is "<trigger>:<param>"; members (non-empty only for set-based triggers
                        // unique_collect/all_collect) carry the glob-expanded family.
                        String[] parts = e.key().split(":", 2);
                        String trigger = (parts.length >= 1 ? parts[0] : "").toLowerCase(java.util.Locale.ROOT);
                        String param = parts.length == 2 ? parts[1] : "";
                        if (!EventTask.KNOWN_TRIGGERS.contains(trigger)) {
                            ChampionshipsCore.getInstance().getLogger().warning(gameLog("未知 event 触发器=" + trigger + "，已跳过"));
                        } else {
                            java.util.Set<Material> memberSet = new java.util.LinkedHashSet<>();
                            for (String m : e.members()) {
                                try {
                                    Material mat = Material.valueOf(m);
                                    if (mat.isItem()) memberSet.add(mat);
                                } catch (IllegalArgumentException ignored) { /* skip unknown member */ }
                            }
                            java.util.Set<EventSubject> subjectSet = new java.util.LinkedHashSet<>();
                            for (String m : e.members()) {
                                subjectSet.add(switch (trigger) {
                                    case "visit_biomes" -> EventSubject.biome(m);
                                    case "kill_family", "kill_unique", "enrage" -> EventSubject.entityType(m);
                                    default -> EventSubject.material(m);
                                });
                            }
                            Material icon = null;
                            if (!memberSet.isEmpty()) {
                                try { icon = Material.valueOf(param); } catch (IllegalArgumentException ignored) { }
                            }
                            b.event(new EventTask(trigger, param, e.count(), e.dimension(), memberSet, icon, subjectSet),
                                    difficulty, e.category());
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                ChampionshipsCore.getInstance().getLogger().warning(gameLog("无效任务=" + e.kind() + "/" + e.key() + "，已跳过"));
            }
        }
        return b.build();
    }

    public static Collector collector() {
        return new Collector();
    }

    private static String gameLog(String message) {
        return Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "卡池", message);
    }

    /** Fluent spec collector that mirrors {@link TaskPool.Builder}; kept for migration tools. */
    public static final class Collector {
        private final List<PoolEntrySpec> entries = new ArrayList<>();

        public Collector item(Material material, Difficulty difficulty) {
            return item(material, 1, difficulty, Dimension.OVERWORLD, null);
        }

        public Collector item(Material material, Difficulty difficulty, Dimension dimension) {
            return item(material, 1, difficulty, dimension, null);
        }

        public Collector item(Material material, int count, Difficulty difficulty,
                              Dimension dimension, String category) {
            entries.add(new PoolEntrySpec(PoolEntrySpec.Kind.ITEM, material.name(), count, difficulty, dimension, category));
            return this;
        }

        public Collector oneOf(java.util.Collection<Material> items, Material display, String name, int count,
                               Difficulty difficulty, Dimension dimension, String category) {
            List<String> members = items.stream().map(Material::name).toList();
            entries.add(PoolEntrySpec.oneOf(members, display == null ? null : display.name(),
                    name, count, difficulty, dimension, category));
            return this;
        }

        public Collector allOf(java.util.Collection<Material> items, Material display, String label, int count,
                               Difficulty difficulty, Dimension dimension, String category) {
            List<String> members = items.stream().map(Material::name).toList();
            entries.add(PoolEntrySpec.allOf(members, display == null ? null : display.name(),
                    label, count, difficulty, dimension, category));
            return this;
        }

        public Collector advancement(String path, Difficulty difficulty) {
            Dimension dim = path.startsWith("nether/") ? Dimension.NETHER
                    : path.startsWith("end/") ? Dimension.THE_END
                    : Dimension.OVERWORLD;
            return advancement(path, difficulty, dim, null);
        }

        public Collector advancement(String path, Difficulty difficulty, Dimension dimension, String category) {
            entries.add(new PoolEntrySpec(PoolEntrySpec.Kind.ADVANCEMENT, path, 1, difficulty, dimension, category));
            return this;
        }

        public Collector mineBlock(Material block, int count, Difficulty difficulty,
                                   Dimension dimension, String category) {
            entries.add(new PoolEntrySpec(PoolEntrySpec.Kind.MINE, block.name(), count, difficulty, dimension, category));
            return this;
        }

        public Collector craft(Material item, int count, Difficulty difficulty,
                               Dimension dimension, String category) {
            entries.add(new PoolEntrySpec(PoolEntrySpec.Kind.CRAFT, item.name(), count, difficulty, dimension, category));
            return this;
        }

        public Collector kill(EntityType entity, int count, Difficulty difficulty,
                              Dimension dimension, String category) {
            entries.add(new PoolEntrySpec(PoolEntrySpec.Kind.KILL, entity.name(), count, difficulty, dimension, category));
            return this;
        }

        public Collector stat(Statistic statistic, int count, Difficulty difficulty,
                              Dimension dimension, String category) {
            entries.add(new PoolEntrySpec(PoolEntrySpec.Kind.STAT, statistic.name(), count, difficulty, dimension, category));
            return this;
        }

        public TaskPoolSpec build() {
            return new TaskPoolSpec(entries);
        }
    }
}
