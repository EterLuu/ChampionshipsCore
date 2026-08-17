package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.Materials;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A unified "event" objective keyed by a {@code trigger} (the mechanic) and a {@code param} (the
 * specific target). Covers draftout's second-category mechanics that don't map to a plain item /
 * statistic / advancement task: eat or drink a specific thing, die by a cause, wear armour, tame /
 * breed / leash / spy a specific entity, hold a status effect, reach a level or location, collect N
 * unique / collect-all of a family, and so on.
 *
 * <p>Completion is driven two ways (see {@link ink.ziip.championshipscore.api.game.bingo.game.BingoRound}):
 * <ul>
 *   <li><b>Instant signal</b> - a Bukkit event listener calls {@code tryCompleteEventSignal} for
 *       triggers like {@code eat}/{@code die}/{@code tame}/{@code breed}/{@code spy} (count 1).</li>
 *   <li><b>Pollable scan</b> - {@code tryCompletePollableEvents} reads the player's current state each
 *       tick for triggers like {@code wear}/{@code effect}/{@code reach}/{@code unique_collect}/
 *       {@code hunger_empty}. Counting triggers ({@code craft_unique}/{@code eat_unique}/…) read a
 *       per-player distinct-set tracker.</li>
 * </ul>
 *
 * <p>{@link #objectiveId()} is {@code event:<trigger>:<param>} with {@code :<count>} appended when
 * count &gt; 1, so the tier list can rank e.g. "craft 20" vs "craft 100" unique items separately.
 * Set-based triggers ({@code unique_collect}/{@code all_collect}) use the display icon name as
 * {@code param} (a stable, wildcard-free token) and carry the expanded member set in {@link #members}.
 */
public record EventTask(String trigger, String param, int count, Dimension dimension,
                        Set<Material> members, @Nullable Material iconOverride,
                        Set<EventSubject> subjects) implements TaskData {

    /** Armour family -> one representative chestplate, for the {@code wear} icon. */
    private static final Map<String, Material> WEAR_ICON = Map.of(
            "LEATHER", Material.LEATHER_CHESTPLATE, "IRON", Material.IRON_CHESTPLATE,
            "GOLDEN", Material.GOLDEN_CHESTPLATE, "DIAMOND", Material.DIAMOND_CHESTPLATE,
            "COPPER", Material.COPPER_CHESTPLATE, "CHAIN", Material.CHAINMAIL_CHESTPLATE);

    /** Status effect -> representative icon, for the {@code effect} trigger. */
    private static final Map<String, Material> EFFECT_ICON = Map.of(
            "LEVITATION", Material.SHULKER_SHELL, "GLOWING", Material.SPECTRAL_ARROW,
            "POISON", Material.SPIDER_EYE, "WEAKNESS", Material.FERMENTED_SPIDER_EYE,
            "ABSORPTION", Material.GOLDEN_APPLE, "JUMP_BOOST", Material.RABBIT_FOOT,
            "NAUSEA", Material.PUFFERFISH, "MINING_FATIGUE", Material.DIAMOND_PICKAXE,
            "BAD_OMEN", Material.OMINOUS_BOTTLE);

    /**
     * Every trigger the framework understands, grouped and validated by {@link EventTrigger}. Used at
     * pool load to reject (with a warning) event tasks whose trigger is misspelled or unimplemented,
     * which would otherwise silently never complete.
     */
    public static final Set<String> KNOWN_TRIGGERS = EventTrigger.keys();

    public EventTask {
        if (dimension == null) dimension = Dimension.OVERWORLD;
        members = members == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(members));
        subjects = subjects == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(subjects));
        count = Math.clamp(count, 1, 64);
        trigger = trigger == null ? "" : trigger.toLowerCase(Locale.ROOT);
        param = param == null ? "" : param;
    }

    /** Convenience for non-set triggers (no member family, no subject set, no icon override). */
    public EventTask(String trigger, String param, int count, Dimension dimension) {
        this(trigger, param, count, dimension, Set.of(), null, Set.of());
    }

    @Override
    public TaskType getType() {
        return TaskType.EVENT;
    }

    @Override
    public String objectiveId() {
        return "event:" + trigger + ":" + param + (count > 1 ? ":" + count : "");
    }

    /** Triggers whose {@code param} names an {@link EntityType}, for entity-atlas map icons. */
    private static final Set<String> ENTITY_ICON_TRIGGERS = Set.of(
            "tame", "breed", "spy", "leash", "enrage");

    /** Death-cause params with an entity sprite in the atlas (mob-caused deaths). */
    private static final Set<String> DIE_ENTITY_ICONS = Set.of(
            "BEE", "IRON_GOLEM", "POLAR_BEAR", "WARDEN");

    /** Death causes whose map subject needs a non-Material sprite (splash harming potion). */
    private static final Key HARMING_SPLASH_POTION_ICON = Key.key("minecraft", "harming_splash_potion");

    /**
     * Override sprite key for event subjects that can't be represented by a single {@link Material}
     * enum (e.g. the effect-coloured splash potion of harming). {@code null} for ordinary subjects.
     */
    public @Nullable Key mapIconKey() {
        if ("die".equals(trigger) && "MAGIC".equalsIgnoreCase(param)) {
            return HARMING_SPLASH_POTION_ICON;
        }
        return null;
    }

    /** Potion type for event subjects shown as potions in the chest GUI (die by magic = harming). */
    public @Nullable org.bukkit.potion.PotionType displayPotionType() {
        return "die".equals(trigger) && "MAGIC".equalsIgnoreCase(param)
                ? org.bukkit.potion.PotionType.HARMING : null;
    }

    /**
     * The entity sprite key to draw on the map card for this task, or {@code null} when the task's
     * icon is an item. The name-tag triggers map their named mob back to its entity sprite as well.
     */
    public @Nullable Key entityIconKey() {
        if (ENTITY_ICON_TRIGGERS.contains(trigger)
                || ("die".equals(trigger) && DIE_ENTITY_ICONS.contains(param.toUpperCase(Locale.ROOT)))) {
            try {
                return EntityType.valueOf(param).key();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if ("name".equals(trigger)) {
            return switch (param.toUpperCase(Locale.ROOT)) {
                case "SHEEP_JEB" -> EntityType.SHEEP.key();
                case "IRON_GOLEM_DINNERBONE" -> EntityType.IRON_GOLEM.key();
                case "GHAST_DINNERBONE" -> EntityType.GHAST.key();
                default -> null;
            };
        }
        return null;
    }

    /** Bottom-left badge sprite for food-eating tasks: the wiki's half-hunger icon. */
    private static final Key HALF_HUNGER_BADGE = Key.key("minecraft", "half_hunger");
    /** Bottom-left badge sprite for the empty-hunger task: the wiki's empty-hunger icon. */
    private static final Key EMPTY_HUNGER_BADGE = Key.key("minecraft", "empty_hunger");
    /** Bottom-left badge for use/interact tasks: the Bedrock mouse-right-click control icon. */
    private static final Key RIGHT_CLICK_BADGE = Key.key("minecraft", "right_click");
    /** Bottom-left badge for angering a zombified piglin: the wiki half-heart icon. */
    private static final Key HALF_HEART_BADGE = Key.key("minecraft", "half_heart");

    /**
     * Symbolic badge shown bottom-left on the map cell as the action badge, mirroring the statistic
     * cell's bottom-left badge. Sprites (not text) are used so the badge reads the same way at card
     * scale as every other icon. Attainment-style triggers ({@link #usesGreenCheckBadge()}) share a
     * drawn green check and any-template triggers ({@link #usesAnyTemplate()}) use the yellow ANY
     * stamp instead; both return no badge here. Eating tasks use the hand-baked wiki half-hunger
     * sprite, the empty-hunger task the empty-hunger sprite, and reaching the bedrock extremes uses
     * the bedrock sprite.
     */
    public @Nullable Key eventBadgeKey() {
        if ("eat".equals(trigger)) return HALF_HUNGER_BADGE;
        if ("hunger_empty".equals(trigger)) return EMPTY_HUNGER_BADGE;
        if ("use".equals(trigger)) return RIGHT_CLICK_BADGE;
        if ("enrage".equals(trigger)) return HALF_HEART_BADGE;
        if ("reach".equals(trigger) && ("BEDROCK".equalsIgnoreCase(param)
                || "NETHER_ROOF".equalsIgnoreCase(param))) {
            return Material.BEDROCK.key();
        }
        Material icon = switch (trigger) {
            case "wear", "wear_full_enchanted", "wear_dyed", "wear_duration" -> Material.ARMOR_STAND;
            case "effect" -> Material.BREWING_STAND;
            case "eat_all" -> Material.COOKIE;
            case "drink" -> Material.GLASS_BOTTLE;
            case "die" -> Material.SKELETON_SKULL;
            case "tame" -> Material.BONE;
            case "breed" -> Material.WHEAT;
            case "spy" -> Material.SPYGLASS;
            case "leash" -> Material.LEAD;
            case "place" -> Material.OAK_SIGN;
            case "name" -> Material.NAME_TAG;
            case "all_collect" -> Material.CHEST;
            case "toot_goat_horn" -> Material.NOTE_BLOCK;
            case "remove_effect_milk" -> Material.GLISTERING_MELON_SLICE;
            case "shield_disabled" -> Material.IRON_INGOT;
            case "shoot_firework_crossbow" -> Material.ARROW;
            case "use_brush" -> Material.BRUSH;
            case "use_golden_dandelion" -> Material.WHEAT;
            case "fill_campfire" -> Material.COOKED_BEEF;
            case "construct_copper_golem" -> Material.CARVED_PUMPKIN;
            case "explode_end_crystal" -> Material.TNT;
            default -> null;
        };
        return icon == null ? null : icon.key();
    }

    /**
     * True for "attain" triggers (reach a level, reach a place, earn N advancements). These cells use
     * one shared green-check corner badge on the map instead of a per-trigger item icon, so every
     * "达到/获得" objective reads with the same unmistakable symbol. The two bedrock-boundary reaches
     * (nether roof, overworld bottom) are exceptions: they carry the bedrock sprite badge instead.
     */
    public boolean usesGreenCheckBadge() {
        return switch (trigger) {
            case "reach_level", "advancement_count" -> true;
            case "reach" -> switch (param.toUpperCase(Locale.ROOT)) {
                case "BEDROCK", "NETHER_ROOF" -> false;
                default -> true;
            };
            default -> false;
        };
    }

    /**
     * Open-ended "any N" triggers - the player chooses arbitrary targets (craft any N items, observe
     * any N mobs, kill any N family members, leash any N species, visit any N biomes, …). These use the
     * one_of "ANY" card template: a yellow ANY corner stamp, the subject icon centred, and no
     * bottom-left action badge.
     */
    private static final Set<String> ANY_TEMPLATE_TRIGGERS = Set.of(
            "break_item", "effect_at_once", "craft_unique", "eat_unique", "breed_unique",
            "leash_unique", "spy_unique", "compost_unique", "kill_family", "kill_unique",
            "visit_biomes", "unique_collect", "stack_of_64", "fill_inventory_unique");

    public boolean usesAnyTemplate() {
        return ANY_TEMPLATE_TRIGGERS.contains(trigger);
    }

    /** Triggers whose name label embeds the count (e.g. "Craft N Unique Items"); others append param. */
    private static final Set<String> COUNT_LABEL_TRIGGERS = Set.of(
            "effect_at_once", "craft_unique", "eat_unique", "eat_all", "breed_unique", "leash_unique",
            "spy_unique", "compost_unique", "advancement_count", "unique_collect", "kill_unique",
            "kill_family", "visit_biomes", "wear_duration", "wear_dyed", "fill_inventory_unique");

    @Override
    public Component getName() {
        MessageService msg = MessageService.global();
        // All event tasks keep the *…* + LIGHT_PURPLE statistic-style title in the chest GUI. Only
        // OneOfTask/AllOfTask item-set cells get the yellow name; the ANY card template for stat/event
        // tasks is a map-cell presentation and doesn't change the GUI name colour.
        var b = Component.text().color(NamedTextColor.LIGHT_PURPLE);
        b.append(Component.text("*")); // same *…* wrapper as StatisticTask
        String prefixKey = "task.event." + trigger;
        // Same trigger, different mechanic -> different wording: CHAIN is "wear any piece", the other
        // armour families are "wear the full set"; count-4 dyed leather is "full set, four colours".
        if ("wear".equals(trigger) && "CHAIN".equalsIgnoreCase(param)) {
            prefixKey = "task.event.wear_any";
        } else if ("wear_dyed".equals(trigger) && count >= 4) {
            prefixKey = "task.event.wear_dyed_full";
        }
        if (msg.has(prefixKey)) {
            b.append(COUNT_LABEL_TRIGGERS.contains(trigger)
                    ? msg.component(prefixKey, count)
                    : msg.component(prefixKey));
        }
        Component paramPart = paramComponent(msg);
        if (paramPart != null) b.append(paramPart);
        b.append(Component.text("*"));
        return b.build();
    }

    private @Nullable Component paramComponent(MessageService msg) {
        return switch (trigger) {
            case "wear" -> Component.text(wearLabel(param));
            case "wear_dyed", "wear_duration", "break_item", "kill_family", "kill_unique",
                    "visit_biomes", "eat_all", "name" -> Component.text(specialLabelOr(param));
            case "effect" -> Component.text(effectLabel(param));
            case "reach_level" -> Component.text(param);
            case "reach" -> Component.text(reachLabel(param));
            case "eat", "drink" -> consumeLabel(param);
            case "die" -> Component.text(dieLabel(param));
            case "tame", "breed", "spy", "leash", "enrage" -> entityComponent(param);
            case "place", "use" -> {
                // Params like HANGING_SIGN are family labels, not Material enum values: prefer a
                // localized label before falling back to the raw material name.
                String special = specialLabel(param);
                yield special != null ? Component.text(special) : materialComponent(param);
            }
            case "unique_collect", "all_collect" -> {
                String special = specialLabel(param);
                yield special != null ? Component.text(special) : materialComponent(param);
            }
            // Standalone triggers (wear_full_enchanted / hunger_empty / effect_at_once / …) have no param.
            default -> null;
        };
    }

    private static String specialLabelOr(String param) {
        String special = specialLabel(param);
        return special != null ? special : param;
    }

    private static String wearLabel(String family) {
        String key = "task.event.wear_family." + family.toLowerCase(Locale.ROOT);
        MessageService msg = MessageService.global();
        return msg.has(key) ? msg.tr(key) : family;
    }

    private static String effectLabel(String effect) {
        String key = "task.effect." + effect.toLowerCase(Locale.ROOT);
        MessageService msg = MessageService.global();
        return msg.has(key) ? msg.tr(key) : effect.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String reachLabel(String place) {
        String key = "task.event.reach_place." + place.toLowerCase(Locale.ROOT);
        MessageService msg = MessageService.global();
        return msg.has(key) ? msg.tr(key) : place;
    }

    private static Component materialComponent(String param) {
        try {
            return BingoComponents.itemName(Material.valueOf(param));
        } catch (IllegalArgumentException e) {
            return Component.text(param);
        }
    }

    /** Eat/drink param: prefer the item name, fall back to a label (e.g. WATER_BOTTLE isn't a material). */
    private static Component consumeLabel(String param) {
        String special = specialLabel(param);
        return special != null ? Component.text(special) : materialComponent(param);
    }

    /** Death-cause param (DROWNING, IRON_GOLEM, FALLING_STALACTITE, …) -> localized label. */
    private static String dieLabel(String param) {
        String key = "task.event.death." + param.toLowerCase(Locale.ROOT);
        MessageService msg = MessageService.global();
        return msg.has(key) ? msg.tr(key) : param.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Optional localized label for params that aren't a material/entity, keyed task.event.label.<param>. */
    private static @Nullable String specialLabel(String param) {
        String key = "task.event.label." + param.toLowerCase(Locale.ROOT);
        MessageService msg = MessageService.global();
        return msg.has(key) ? msg.tr(key) : null;
    }

    private static Component entityComponent(String param) {
        try {
            return BingoComponents.entityName(EntityType.valueOf(param));
        } catch (IllegalArgumentException e) {
            return Component.text(param);
        }
    }

    @Override
    public Component[] getItemDescription() {
        return new Component[]{MessageService.global().component("task.event_goal")};
    }

    @Override
    public Component getChatDescription() {
        return Component.text().append(getItemDescription()).build();
    }

    @Override
    public boolean shouldItemGlow() {
        return true;
    }

    @Override
    public Material getDisplayMaterial(CardDisplayInfo context) {
        if (iconOverride != null) return iconOverride;
        return switch (trigger) {
            case "wear" -> WEAR_ICON.getOrDefault(param.toUpperCase(Locale.ROOT), Material.LEATHER_CHESTPLATE);
            case "wear_full_enchanted" -> Material.DIAMOND_CHESTPLATE;
            case "wear_dyed", "wear_duration" -> Material.LEATHER_CHESTPLATE;
            case "effect" -> EFFECT_ICON.getOrDefault(param.toUpperCase(Locale.ROOT), Material.POTION);
            case "effect_at_once" -> Material.MILK_BUCKET;
            case "reach_level" -> Material.EXPERIENCE_BOTTLE;
            case "reach" -> switch (param.toUpperCase(Locale.ROOT)) {
                case "BEDROCK" -> Material.DEEPSLATE;
                case "HEIGHT_LIMIT" -> Material.SCAFFOLDING;
                case "NETHER_ROOF" -> Material.NETHERRACK;
                default -> Material.PAPER;
            };
            case "hunger_empty" -> Material.ROTTEN_FLESH;
            case "eat", "drink" -> materialOr(param, Material.APPLE);
            case "eat_all" -> Material.MUSHROOM_STEW;
            case "die" -> switch (param.toUpperCase(Locale.ROOT)) {
                case "DROWNING" -> Material.WATER_BUCKET;
                case "VOID" -> Material.BEDROCK;
                case "FREEZE" -> Material.POWDER_SNOW_BUCKET;
                case "MAGIC" -> Material.SPLASH_POTION; // map icon uses the harming splash sprite
                case "FIREWORK" -> Material.FIREWORK_ROCKET;
                case "FALLING_STALACTITE" -> Material.POINTED_DRIPSTONE;
                case "BERRY_BUSH" -> Material.SWEET_BERRIES;
                default -> materialOr(param, Material.SKELETON_SKULL);
            };
            case "tame", "breed", "spy", "leash", "enrage" -> spawnEggOr(param);
            case "break_item" -> "ARMOR".equalsIgnoreCase(param) ? Material.IRON_CHESTPLATE : Material.STONE_PICKAXE;
            case "place" -> "HANGING_SIGN".equalsIgnoreCase(param)
                    ? Material.OAK_HANGING_SIGN : materialOr(param, Material.PAINTING);
            case "use" -> materialOr(param, Material.COMPOSTER);
            case "name" -> Material.NAME_TAG;
            case "toot_goat_horn" -> Material.GOAT_HORN;
            case "remove_effect_milk" -> Material.MILK_BUCKET;
            case "shield_disabled" -> Material.SHIELD;
            case "shoot_firework_crossbow" -> Material.FIREWORK_ROCKET;
            case "use_brush" -> Material.SUSPICIOUS_GRAVEL;
            case "use_golden_dandelion" -> Material.GOLDEN_DANDELION;
            case "fill_campfire" -> Material.CAMPFIRE;
            case "construct_copper_golem" -> Material.COPPER_BLOCK;
            case "explode_end_crystal" -> Material.END_CRYSTAL;
            case "fill_inventory_unique" -> Material.CHEST;
            case "stack_of_64" -> Material.COBBLESTONE;
            case "craft_unique" -> Material.CRAFTING_TABLE;
            case "eat_unique" -> Material.APPLE;
            case "breed_unique" -> Material.WHEAT;
            case "spy_unique" -> Material.SPYGLASS;
            case "leash_unique" -> Material.LEAD;
            case "compost_unique" -> Material.COMPOSTER;
            case "advancement_count" -> Material.KNOWLEDGE_BOOK;
            case "visit_biomes" -> Material.NETHERRACK;
            case "kill_family", "kill_unique" -> "ARTHROPOD".equalsIgnoreCase(param)
                    ? Material.SPIDER_EYE : Material.ZOMBIE_HEAD;
            case "unique_collect", "all_collect" ->
                    members.isEmpty() ? Material.PAPER : members.iterator().next();
            default -> Material.PAPER;
        };
    }

    private static Material materialOr(String param, Material fallback) {
        try {
            return Material.valueOf(param);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Material spawnEggOr(String entityType) {
        try {
            Material egg = Materials.fromKey("minecraft:" + EntityType.valueOf(entityType).key().value() + "_spawn_egg");
            return egg.isItem() ? egg : Material.PAPER;
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    @Override
    public int getRequiredAmount() {
        return count;
    }

    @Override
    public TaskData setRequiredAmount(int newAmount) {
        return new EventTask(trigger, param, newAmount, dimension, members, iconOverride, subjects);
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return other instanceof EventTask e && trigger.equals(e.trigger) && param.equals(e.param)
                && count == e.count && subjects.equals(e.subjects);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventTask e = (EventTask) o;
        return count == e.count && trigger.equals(e.trigger) && param.equals(e.param)
                && subjects.equals(e.subjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trigger, param, count, subjects);
    }
}
