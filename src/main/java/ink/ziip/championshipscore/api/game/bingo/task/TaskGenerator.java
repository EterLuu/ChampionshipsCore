package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TagFilters;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPool;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolEntry;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSource;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates the task list for a card from the active {@link TaskPoolSource}, filtered by included
 * task types and disabled dimensions, then sampled with a category-aware difficulty-weighted random
 * selection. Category-aware means each generated card carries at most one task from any given
 * category, so a small family can't crowd out the rest of the tier.
 *
 * <p>Simplified port: the mode/remix-specific knobs (starter kit, seed tasks, nether fraction,
 * differential cards, difficulty weights) were dropped for CC's single fixed points mode. Difficulty
 * comes straight from each entry's tier; only the static tag layer plus per-round dimension excludes
 * /caps remain.
 */
public final class TaskGenerator {
    public record GeneratorSettings(long seed,
                                    Set<TaskData.TaskType> includedTypes,
                                    CardSize size,
                                    Set<String> extraExcludedTags,
                                    Map<String, Integer> extraTagCaps) {
        public GeneratorSettings {
            extraExcludedTags = extraExcludedTags == null ? Set.of() : Set.copyOf(extraExcludedTags);
            extraTagCaps = extraTagCaps == null ? Map.of() : Map.copyOf(extraTagCaps);
        }
    }

    private static final TaskData DEFAULT_TASK = new ItemTask(Material.DIRT, 1);

    /**
     * Per-tier selection weights indexed by {@link ink.ziip.championshipscore.api.game.bingo.task.pool.Difficulty#ordinal()},
     * overriding each tier's built-in weight. A weight of 0 excludes that tier entirely. Null = use the
     * built-in tier weights. Installed once at startup from the bingo config
     * (default {@code [3,5,2,1,0]} = EASY:MEDIUM:ADVANCED:HARD = 3:5:2:1, VERY_HARD excluded).
     */
    private static volatile int[] difficultyWeights;

    public static void setDifficultyWeights(int[] weights) {
        difficultyWeights = (weights == null) ? null : weights.clone();
    }

    /** Chance an item-collect task is rerolled into a "craft that item" task (only if it's craftable). */
    private static final double CRAFT_CONVERSION_CHANCE = 0.05;

    /**
     * Selection-weight multiplier for {@code one_of} buckets. Each set is its own standalone bucket, so
     * the ~20 sets would otherwise compete as ~20 independent MEDIUM-weight buckets and crowd a card
     * with several "any X" cells. Halving their weight keeps them varied but down to roughly one per
     * card, without mislabeling their difficulty tier (which difficulty-filtered modes rely on).
     */
    private static final double ONE_OF_WEIGHT_FACTOR = 0.5;

    /** Memoised craftability per material; populated lazily from the server's recipe registry. */
    private static final Map<Material, Boolean> CRAFTABLE_CACHE = new ConcurrentHashMap<>();

    public static List<GameTask> generateCardTasks(GeneratorSettings settings) {
        Random rng = settings.seed() == 0 ? new Random() : new Random(settings.seed());

        TaskPool pool = TaskPoolSource.pool().filter(settings.includedTypes(), Set.of());
        // Merge the static config tag layer with this round's dynamic dimension rules.
        TagFilters filters = TagFilters.active().merged(settings.extraExcludedTags(), settings.extraTagCaps());

        List<TaskPoolEntry> entries = new ArrayList<>(pool.entries());
        // Tag layer: drop any objective carrying an excluded tag (unobtainable, a disabled dimension, …).
        entries.removeIf(e -> filters.isExcluded(e.task()));

        int fullCardSize = settings.size().fullCardSize;

        // Subjects already placed on this card, shared across every sampling call so two tasks that
        // target the same item can never co-exist.
        Set<String> usedSubjects = new HashSet<>();
        // Per-card running counts for capped tags, shared across every sampling call.
        Map<String, Integer> tagCounts = new HashMap<>();

        List<TaskData> picked = weightedSample(entries, fullCardSize, rng, usedSubjects, filters, tagCounts);

        Collections.shuffle(picked, rng);
        return picked.stream().map(t -> new GameTask(maybeCraftify(t, rng))).toList();
    }

    /**
     * Category-aware weighted sampling without replacement. Entries are grouped by category (null-category
     * entries each form a singleton bucket). Each bucket gets a single Efraimidis–Spirakis key
     * {@code u^(1/weight)}; the {@code count} largest keys win, and from each winning bucket one member is
     * picked uniformly at random — so a category's chance is independent of how many siblings it holds.
     * Pads with {@link #DEFAULT_TASK} if too few buckets exist.
     */
    private static List<TaskData> weightedSample(List<TaskPoolEntry> entries, int count, Random rng,
                                                  Set<String> usedSubjects, TagFilters filters,
                                                  Map<String, Integer> tagCounts) {
        Map<String, List<TaskPoolEntry>> categorised = new HashMap<>();
        List<List<TaskPoolEntry>> buckets = new ArrayList<>();
        for (TaskPoolEntry entry : entries) {
            if (entry.category() == null) {
                buckets.add(List.of(entry));
            } else {
                categorised.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
            }
        }
        buckets.addAll(categorised.values());

        record Keyed(List<TaskPoolEntry> bucket, double key) {
        }
        List<Keyed> keyed = new ArrayList<>(buckets.size());
        for (List<TaskPoolEntry> bucket : buckets) {
            int w = weightFor(bucket.get(0));
            if (w <= 0) continue; // tier excluded (e.g. VERY_HARD weight 0) or empty
            // Down-weight broad one_of sets so several don't crowd a single card (see field doc).
            if (bucket.get(0).task() instanceof OneOfTask) {
                w = Math.max(1, (int) Math.round(w * ONE_OF_WEIGHT_FACTOR));
            }
            double key = Math.pow(rng.nextDouble(), 1.0 / w);
            keyed.add(new Keyed(bucket, key));
        }
        keyed.sort(Comparator.comparingDouble(Keyed::key).reversed());

        List<TaskData> picked = new ArrayList<>(count);
        for (Keyed k : keyed) {
            if (picked.size() >= count) break;
            TaskData choice = chooseMember(k.bucket(), usedSubjects, rng, filters, tagCounts);
            if (choice == null) continue; // bucket exhausted: every member collides or hits a tag cap
            addSubjectKeys(choice, usedSubjects);
            addTagCounts(choice, filters, tagCounts);
            picked.add(choice);
        }
        while (picked.size() < count) {
            picked.add(DEFAULT_TASK);
        }
        return picked;
    }

    private static TaskData chooseMember(List<TaskPoolEntry> bucket, Set<String> usedSubjects, Random rng,
                                         TagFilters filters, Map<String, Integer> tagCounts) {
        int start = rng.nextInt(bucket.size());
        for (int offset = 0; offset < bucket.size(); offset++) {
            TaskData task = bucket.get((start + offset) % bucket.size()).task();
            if (!hasCollision(task, usedSubjects) && !exceedsCap(task, filters, tagCounts)) {
                return task;
            }
        }
        return null;
    }

    private static boolean exceedsCap(TaskData task, TagFilters filters, Map<String, Integer> tagCounts) {
        if (!filters.hasCaps()) return false;
        for (String tag : filters.tagsOf(task)) {
            Integer cap = filters.cap(tag);
            if (cap != null && tagCounts.getOrDefault(tag, 0) >= cap) return true;
        }
        return false;
    }

    private static void addTagCounts(TaskData task, TagFilters filters, Map<String, Integer> tagCounts) {
        if (!filters.hasCaps()) return;
        for (String tag : filters.tagsOf(task)) {
            if (filters.cap(tag) != null) tagCounts.merge(tag, 1, Integer::sum);
        }
    }

    private static boolean hasCollision(TaskData task, Set<String> usedSubjects) {
        for (String subject : subjectsOf(task)) {
            if (usedSubjects.contains(subject)) return true;
        }
        return false;
    }

    private static void addSubjectKeys(TaskData task, Set<String> usedSubjects) {
        usedSubjects.addAll(subjectsOf(task));
    }

    /** All subject keys a task occupies: a single item/color for most kinds, every member for a set. */
    private static Set<String> subjectsOf(TaskData task) {
        Set<String> out = new HashSet<>();
        if (task instanceof OneOfTask set) {
            for (Material material : set.items()) {
                out.add("item:" + material);
            }
            return out;
        }
        if (task instanceof PotionTask potion) {
            // Claim the potion's material so an effect potion and the generic "collect a potion" (or a
            // second potion of the same form) can't both land on one card.
            out.add("item:" + potion.form().material.name());
            return out;
        }
        String subject = subjectKey(task);
        if (subject != null) out.add(subject);
        String color = colorKey(task);
        if (color != null) out.add(color);
        return out;
    }

    private static String subjectKey(TaskData task) {
        if (task instanceof ItemTask itemTask) {
            return "item:" + itemTask.itemType();
        }
        if (task instanceof StatisticTask statisticTask && statisticTask.statistic().itemType() != null) {
            return "item:" + statisticTask.statistic().itemType();
        }
        return null;
    }

    private static final Set<String> COLOR_SUFFIXES = Set.of(
            "_DYE", "_WOOL", "_CARPET", "_BED", "_BANNER",
            "_STAINED_GLASS", "_STAINED_GLASS_PANE",
            "_CONCRETE", "_CONCRETE_POWDER",
            "_TERRACOTTA", "_GLAZED_TERRACOTTA",
            "_CANDLE", "_HARNESS"
    );

    private static String colorKey(TaskData task) {
        Material material = null;
        if (task instanceof ItemTask itemTask) {
            material = itemTask.itemType();
        } else if (task instanceof StatisticTask statisticTask && statisticTask.statistic().itemType() != null) {
            material = statisticTask.statistic().itemType();
        }
        return colorKeyFor(material);
    }

    private static String colorKeyFor(Material material) {
        if (material == null) return null;
        String name = material.name();
        for (String suffix : COLOR_SUFFIXES) {
            if (name.endsWith(suffix)) {
                String prefix = name.substring(0, name.length() - suffix.length());
                if (!prefix.isEmpty()) {
                    return "color:" + prefix;
                }
            }
        }
        return null;
    }

    /** Selection weight for a bucket's representative entry: the configured tier override, else the
     *  tier's built-in weight. A returned 0 excludes the bucket. */
    private static int weightFor(TaskPoolEntry entry) {
        int[] dw = difficultyWeights;
        if (dw == null) return entry.difficulty().weight;
        int ord = entry.difficulty().ordinal();
        return ord < dw.length ? dw[ord] : 0;
    }

    /**
     * With probability {@link #CRAFT_CONVERSION_CHANCE}, rerolls an item-collect task into a
     * "craft that item" task — but only when the item is actually craftable. The subject identity is
     * preserved, so this never reintroduces a collect/craft duplicate on one card.
     */
    private static TaskData maybeCraftify(TaskData task, Random rng) {
        if (!(task instanceof ItemTask itemTask)) return task;
        if (rng.nextDouble() >= CRAFT_CONVERSION_CHANCE) return task;
        if (!isCraftable(itemTask.itemType())) return task;
        return new StatisticTask(new StatisticHandle(Statistic.CRAFT_ITEM, itemTask.itemType()),
                itemTask.count(), itemTask.dimension());
    }

    private static boolean isCraftable(Material material) {
        return CRAFTABLE_CACHE.computeIfAbsent(material, m -> {
            if (!m.isItem()) return false;
            for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(m))) {
                if (recipe instanceof CraftingRecipe) return true;
            }
            return false;
        });
    }
}
