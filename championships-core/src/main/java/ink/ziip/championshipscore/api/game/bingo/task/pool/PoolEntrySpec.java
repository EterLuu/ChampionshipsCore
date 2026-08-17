package ink.ziip.championshipscore.api.game.bingo.task.pool;

import java.util.List;

/**
 * Serialisable, Bukkit-light description of one pooled task. Behind configurable {@code cards/*.yml}
 * pools: {@link TaskPoolSpec} parses the file into these and resolves them against the live server
 * (materials, entities, statistics, advancements) when building the actual {@link TaskPool}.
 */
public record PoolEntrySpec(Kind kind, String key, int count, Difficulty difficulty,
                            Dimension dimension, String category, List<String> members, String name) {

    public enum Kind {
        ITEM,
        ADVANCEMENT,
        MINE,
        CRAFT,
        KILL,
        STAT,
        ONE_OF,
        /** Effect-specific potion: {@link #key} is {@code <form>:<effect>}, e.g. {@code splash:strength}. */
        POTION,
        /** Complete-the-set objective ({@code all_of}): hold every listed item at once. {@link #key} is the representative display item. */
        ALL_OF,
        /** Unified event objective: {@link #key} is {@code <trigger>:<param>} (count appended for counting
         *  triggers by {@link ink.ziip.championshipscore.api.game.bingo.task.EventTask#objectiveId()}). Covers
         *  draftout's eat/die/wear/tame/breed/spy/leash/effect/reach/unique_collect/… mechanics. */
        EVENT
    }

    public PoolEntrySpec {
        if (dimension == null) dimension = Dimension.OVERWORLD;
        if (category != null && category.isBlank()) category = null;
        members = members == null ? List.of() : List.copyOf(members);
        if (name != null && name.isBlank()) name = null;
    }

    /** Convenience constructor for the single-key kinds (everything except {@link Kind#ONE_OF}). */
    public PoolEntrySpec(Kind kind, String key, int count, Difficulty difficulty,
                         Dimension dimension, String category) {
        this(kind, key, count, difficulty, dimension, category, List.of(), null);
    }

    /** Factory for a {@link Kind#ONE_OF} entry. */
    public static PoolEntrySpec oneOf(List<String> members, String displayItem, String name, int count,
                                      Difficulty difficulty, Dimension dimension, String category) {
        return new PoolEntrySpec(Kind.ONE_OF, displayItem == null ? "" : displayItem, count,
                difficulty, dimension, category, members, name);
    }

    /** Factory for a {@link Kind#ALL_OF} entry. {@code label} is the optional {@code task.family.*} token. */
    public static PoolEntrySpec allOf(List<String> members, String displayItem, String label, int count,
                                      Difficulty difficulty, Dimension dimension, String category) {
        String key = displayItem == null || displayItem.isBlank()
                ? (members.isEmpty() ? "" : members.get(0)) : displayItem;
        return new PoolEntrySpec(Kind.ALL_OF, key, count,
                difficulty, dimension, category, members, label);
    }

    /**
     * Stable identifier for this objective, used by external tier lists and tag rules to target it.
     * Items use the bare material name (so {@code *_PLANKS} matches); other kinds carry a kind prefix:
     * {@code mine:}, {@code craft:}, {@code kill:}, {@code stat:}, {@code advancement:}, {@code set:},
     * {@code all:}, {@code event:}. A {@link Kind#ONE_OF} set is keyed by its {@code name} when
     * present, else its display item; a {@link Kind#ALL_OF} set is keyed by its display item.
     */
    public String objectiveId() {
        return switch (kind) {
            case ITEM -> key;
            case MINE -> "mine:" + key;
            case CRAFT -> "craft:" + key;
            case KILL -> "kill:" + key;
            case STAT -> "stat:" + key;
            case ADVANCEMENT -> "advancement:" + key;
            case ONE_OF -> "set:" + (name != null ? name : key);
            case ALL_OF -> "all:" + key;
            case POTION -> "potion:" + key; // key is already "<form>:<effect>"
            case EVENT -> "event:" + key + (count > 1 ? ":" + count : ""); // key is "<trigger>:<param>"
        };
    }
}
