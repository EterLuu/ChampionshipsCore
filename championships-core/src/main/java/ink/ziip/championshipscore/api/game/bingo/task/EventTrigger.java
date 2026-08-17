package ink.ziip.championshipscore.api.game.bingo.task;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Central catalogue of every {@link EventTask} trigger, grouped by completion model.
 *
 * <p>This is the architectural backbone behind the {@code events:} card-pool section:
 * <ul>
 *   <li>{@link Mode#SIGNAL} - a discrete Bukkit event observed by {@code BingoHandler} and routed
 *       through {@code BingoRound#tryCompleteEventSignal}.</li>
 *   <li>{@link Mode#STATE} - a property of the player/world that can be queried on the tracker pass
 *       (armour, effects, inventory sets, current biome, …).</li>
 *   <li>{@link Mode#TRACKED} - session-long accumulation over a per-player bucket (distinct sets,
 *       counters, elapsed time) fed by listeners and resolved on the tracker pass.</li>
 * </ul>
 *
 * <p>All access to the trigger name stays string-based in {@link EventTask} so the YAML objective ids
 * remain stable; this enum is the single source of truth for validity and for poll-vs-signal routing.
 */
public enum EventTrigger {

    // ── STATE: queryable on each tracker pass ──────────────────────────────────────────────────────
    WEAR("wear", Mode.STATE),
    WEAR_FULL_ENCHANTED("wear_full_enchanted", Mode.STATE),
    WEAR_DYED("wear_dyed", Mode.STATE),
    EFFECT("effect", Mode.STATE),
    EFFECT_AT_ONCE("effect_at_once", Mode.STATE),
    REACH_LEVEL("reach_level", Mode.STATE),
    REACH("reach", Mode.STATE),
    HUNGER_EMPTY("hunger_empty", Mode.STATE),
    SPY("spy", Mode.STATE),
    UNIQUE_COLLECT("unique_collect", Mode.STATE),
    ALL_COLLECT("all_collect", Mode.STATE),
    STACK_OF_64("stack_of_64", Mode.STATE),
    FILL_INVENTORY_UNIQUE("fill_inventory_unique", Mode.STATE),

    // ── SIGNAL: one-shot events observed by listeners ─────────────────────────────────────────────
    EAT("eat", Mode.SIGNAL),
    DRINK("drink", Mode.SIGNAL),
    DIE("die", Mode.SIGNAL),
    TAME("tame", Mode.SIGNAL),
    BREED("breed", Mode.SIGNAL),
    LEASH("leash", Mode.SIGNAL),
    BREAK_ITEM("break_item", Mode.SIGNAL),
    PLACE("place", Mode.SIGNAL),
    USE("use", Mode.SIGNAL),
    NAME("name", Mode.SIGNAL),
    TOOT_GOAT_HORN("toot_goat_horn", Mode.SIGNAL),
    REMOVE_EFFECT_MILK("remove_effect_milk", Mode.SIGNAL),
    SHIELD_DISABLED("shield_disabled", Mode.SIGNAL),
    SHOOT_FIREWORK_CROSSBOW("shoot_firework_crossbow", Mode.SIGNAL),
    USE_BRUSH("use_brush", Mode.SIGNAL),
    USE_GOLDEN_DANDELION("use_golden_dandelion", Mode.SIGNAL),
    FILL_CAMPFIRE("fill_campfire", Mode.SIGNAL),
    CONSTRUCT_COPPER_GOLEM("construct_copper_golem", Mode.SIGNAL),
    ENRAGE("enrage", Mode.SIGNAL),
    EXPLODE_END_CRYSTAL("explode_end_crystal", Mode.SIGNAL),

    // ── TRACKED: per-player distinct-set / counter / elapsed-time accumulation ────────────────────
    CRAFT_UNIQUE("craft_unique", Mode.TRACKED),
    EAT_UNIQUE("eat_unique", Mode.TRACKED),
    EAT_ALL("eat_all", Mode.TRACKED),
    BREED_UNIQUE("breed_unique", Mode.TRACKED),
    LEASH_UNIQUE("leash_unique", Mode.STATE), // resolved from the mobs currently leashed to the player
    SPY_UNIQUE("spy_unique", Mode.TRACKED),
    COMPOST_UNIQUE("compost_unique", Mode.TRACKED),
    ADVANCEMENT_COUNT("advancement_count", Mode.TRACKED),
    KILL_FAMILY("kill_family", Mode.TRACKED),
    KILL_UNIQUE("kill_unique", Mode.TRACKED),
    VISIT_BIOMES("visit_biomes", Mode.TRACKED),
    WEAR_DURATION("wear_duration", Mode.TRACKED);

    /** How the trigger completes: by event signal, by current state, or by session accumulation. */
    public enum Mode {
        SIGNAL,
        STATE,
        TRACKED
    }

    private final String key;
    private final Mode mode;

    EventTrigger(String key, Mode mode) {
        this.key = key;
        this.mode = mode;
    }

    public String key() {
        return key;
    }

    public Mode mode() {
        return mode;
    }

    /** True when the trigger is resolved on the tracker pass (STATE or TRACKED). */
    public boolean isPollable() {
        return mode != Mode.SIGNAL;
    }

    @Nullable
    public static EventTrigger fromKey(@Nullable String key) {
        if (key == null) return null;
        String normalised = key.toLowerCase(Locale.ROOT);
        for (EventTrigger trigger : values()) {
            if (trigger.key.equals(normalised)) return trigger;
        }
        return null;
    }

    public static boolean isPollable(@Nullable String key) {
        EventTrigger trigger = fromKey(key);
        return trigger != null && trigger.isPollable();
    }

    /** All valid trigger keys, for the pool loader's validation set. */
    public static Set<String> keys() {
        Set<String> keys = new LinkedHashSet<>();
        for (EventTrigger trigger : values()) keys.add(trigger.key);
        return Collections.unmodifiableSet(keys);
    }
}
