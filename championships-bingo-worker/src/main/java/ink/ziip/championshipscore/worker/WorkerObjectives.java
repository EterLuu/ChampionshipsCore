package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/** Manifest task compiler and Folia-safe per-player objective observer. */
final class WorkerObjectives {
    private static final Set<String> SIGNAL_TRIGGERS = Set.of(
            "eat", "drink", "die", "tame", "breed", "leash", "break_item", "place", "use",
            "name", "toot_goat_horn", "remove_effect_milk", "shield_disabled",
            "shoot_firework_crossbow", "use_brush", "use_golden_dandelion", "fill_campfire",
            "construct_copper_golem", "enrage", "explode_end_crystal");
    private volatile List<Objective> objectives;
    private volatile List<Objective> pollingObjectives;
    private volatile Map<String, List<Integer>> advancementCells;
    private final Map<UUID, Map<Integer, Integer>> statisticBaselines = new ConcurrentHashMap<>();
    private final Map<UUID, Double> boatTravelCentimeters = new ConcurrentHashMap<>();
    private final EventProgress eventProgress = new EventProgress();

    WorkerObjectives(List<BingoTaskSpec> specs) {
        replace(specs);
    }

    synchronized void replace(List<BingoTaskSpec> specs) {
        List<Objective> parsed = new ArrayList<>(specs.size());
        for (BingoTaskSpec spec : specs) parsed.add(parse(spec));
        this.objectives = List.copyOf(parsed);
        this.pollingObjectives = parsed.stream()
                .filter(objective -> !(objective instanceof AdvancementObjective)
                        && (!(objective instanceof EventObjective event) || event.pollable()))
                .toList();
        Map<String, List<Integer>> cellsByAdvancement = new HashMap<>();
        for (Objective objective : parsed) {
            if (objective instanceof AdvancementObjective advancement) {
                cellsByAdvancement.computeIfAbsent(advancement.key(), ignored -> new ArrayList<>())
                        .add(advancement.cellIndex());
            }
        }
        cellsByAdvancement.replaceAll((ignored, cells) -> List.copyOf(cells));
        this.advancementCells = Map.copyOf(cellsByAdvancement);
        statisticBaselines.clear();
        boatTravelCentimeters.clear();
    }

    void captureBaselines(Player player) {
        Map<Integer, Integer> values = new HashMap<>();
        for (Objective objective : objectives) {
            if (objective instanceof StatisticObjective statistic) {
                values.put(statistic.cellIndex(), statistic.read(player));
            }
        }
        statisticBaselines.put(player.getUniqueId(), Map.copyOf(values));
    }

    void prepareParticipant(Player player) {
        for (Objective objective : objectives) {
            if (!(objective instanceof AdvancementObjective advancementObjective)) continue;
            AdvancementProgress progress = player.getAdvancementProgress(advancementObjective.advancement());
            for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criterion);
            }
        }
        captureBaselines(player);
    }

    List<Integer> matching(Player player, IntPredicate eligibleCell) {
        Map<Integer, Integer> baselines = statisticBaselines.getOrDefault(player.getUniqueId(), Map.of());
        List<Integer> matches = new ArrayList<>();
        for (Objective objective : pollingObjectives) {
            if (!eligibleCell.test(objective.cellIndex())) continue;
            int baseline = baselines.getOrDefault(objective.cellIndex(), 0);
            boolean matched = objective.matches(player, baseline);
            if (objective instanceof StatisticObjective statistic
                    && statistic.statistic() == Statistic.BOAT_ONE_CM) {
                int vanillaDelta = statistic.read(player) - baseline;
                double tracked = boatTravelCentimeters.getOrDefault(player.getUniqueId(), 0.0D);
                int trackedDelta = tracked >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(tracked);
                matched = Math.max(vanillaDelta, trackedDelta) >= statistic.target();
            }
            if (matched) {
                matches.add(objective.cellIndex());
            }
        }
        return matches;
    }

    List<Integer> matchingAdvancement(Advancement advancement) {
        return advancementCells.getOrDefault(advancement.key().asString(), List.of());
    }

    List<Integer> matchingAdvancement(Advancement advancement, IntPredicate eligibleCell) {
        return matchingAdvancement(advancement).stream().filter(eligibleCell::test).toList();
    }

    List<Integer> matchingEventSignal(Player player, String trigger, String param, IntPredicate eligibleCell) {
        List<Integer> matches = new ArrayList<>();
        for (Objective objective : objectives) {
            if (!(objective instanceof EventObjective event) || event.count != 1 || event.pollable()) continue;
            if (!eligibleCell.test(event.cellIndex)) continue;
            if (event.trigger.equalsIgnoreCase(trigger) && event.param.equalsIgnoreCase(param)) {
                matches.add(event.cellIndex);
            }
        }
        return matches;
    }

    void recordDistinct(Player player, String bucket, String value) {
        eventProgress.recordDistinct(player.getUniqueId(), bucket, value);
    }

    void recordCount(Player player, String bucket) {
        eventProgress.increment(player.getUniqueId(), bucket);
    }

    void recordBoatMovement(Player player, double centimeters) {
        if (player == null || !Double.isFinite(centimeters) || centimeters <= 0.0D) return;
        boatTravelCentimeters.merge(player.getUniqueId(), centimeters, Double::sum);
    }

    private Objective parse(BingoTaskSpec spec) {
        Map<String, String> attributes = spec.attributes();
        return switch (spec.taskType().toLowerCase(Locale.ROOT)) {
            case "item" -> new ItemObjective(spec.cellIndex(),
                    Set.of(material(attributes, "material")), integer(attributes, "count", 1), null, MatchMode.TOTAL);
            case "potion" -> new ItemObjective(spec.cellIndex(),
                    Set.of(material(attributes, "material")), integer(attributes, "count", 1),
                    required(attributes, "effect").toLowerCase(Locale.ROOT), MatchMode.TOTAL);
            case "item_set" -> new ItemObjective(spec.cellIndex(), materials(attributes),
                    integer(attributes, "count", 1), null, MatchMode.SINGLE);
            case "all_of" -> new ItemObjective(spec.cellIndex(), materials(attributes),
                    integer(attributes, "count", 1), null, MatchMode.ALL);
            case "event" -> event(spec);
            case "advancement" -> advancement(spec.cellIndex(), required(attributes, "key"));
            case "statistic" -> statistic(spec);
            default -> throw new IllegalArgumentException(
                    "Unsupported Bingo task type " + spec.taskType() + " at cell " + spec.cellIndex());
        };
    }

    private EventObjective event(BingoTaskSpec spec) {
        Map<String, String> attributes = spec.attributes();
        String trigger = required(attributes, "trigger").toLowerCase(Locale.ROOT);
        String param = attributes.getOrDefault("param", "");
        int count = integer(attributes, "count", 1);
        Set<Material> members = optionalMaterials(attributes.get("members"));
        Set<String> subjects = new java.util.LinkedHashSet<>();
        String rawSubjects = attributes.get("subjects");
        if (rawSubjects != null && !rawSubjects.isBlank()) {
            for (String subject : rawSubjects.split(",")) {
                if (!subject.isBlank()) subjects.add(subject.trim());
            }
        }
        return new EventObjective(spec.cellIndex(), trigger, param, count, members, Set.copyOf(subjects));
    }

    private static StatisticObjective statistic(BingoTaskSpec spec) {
        Map<String, String> attributes = spec.attributes();
        Statistic statistic;
        try {
            statistic = Statistic.valueOf(required(attributes, "statistic"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown statistic at cell " + spec.cellIndex(), invalid);
        }
        Material material = optionalMaterial(attributes.get("material"));
        EntityType entity = null;
        String entityName = attributes.get("entity");
        if (entityName != null && !entityName.isBlank()) {
            try {
                entity = EntityType.valueOf(entityName);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown statistic entity " + entityName, invalid);
            }
        }
        return new StatisticObjective(spec.cellIndex(), statistic, material, entity,
                integer(attributes, "target", integer(attributes, "count", 1)));
    }

    private static AdvancementObjective advancement(int cellIndex, String rawKey) {
        NamespacedKey key = NamespacedKey.fromString(rawKey);
        Advancement advancement = key == null ? null : org.bukkit.Bukkit.getAdvancement(key);
        if (advancement == null) {
            throw new IllegalArgumentException("Unknown advancement " + rawKey + " at cell " + cellIndex);
        }
        return new AdvancementObjective(cellIndex, advancement.key().asString(), advancement);
    }

    private static Set<Material> materials(Map<String, String> attributes) {
        String raw = required(attributes, "materials");
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : raw.split(",")) materials.add(parseMaterial(name.trim()));
        if (materials.isEmpty()) throw new IllegalArgumentException("item_set materials must not be empty");
        return Set.copyOf(materials);
    }

    private static Set<Material> optionalMaterials(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : raw.split(",")) materials.add(parseMaterial(name.trim()));
        return Set.copyOf(materials);
    }

    private static Material material(Map<String, String> attributes, String key) {
        return parseMaterial(required(attributes, key));
    }

    private static Material optionalMaterial(String value) {
        return value == null || value.isBlank() ? null : parseMaterial(value);
    }

    private static Material parseMaterial(String name) {
        String normalized = name.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        Material material = Material.matchMaterial(normalized);
        if (material == null) throw new IllegalArgumentException("Unknown material " + name);
        return material;
    }

    private static int integer(Map<String, String> attributes, String key, int fallback) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) throw new IllegalArgumentException(key + " must be positive");
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid integer attribute " + key, invalid);
        }
    }

    private static String required(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing task attribute " + key);
        return value;
    }

    private sealed interface Objective permits ItemObjective, AdvancementObjective, StatisticObjective, EventObjective {
        int cellIndex();

        boolean matches(Player player, int baseline);
    }

    private enum MatchMode { TOTAL, SINGLE, ALL }

    private record ItemObjective(int cellIndex, Set<Material> materials, int count, String potionEffect,
                                 MatchMode mode)
            implements Objective {
        @Override
        public boolean matches(Player player, int ignored) {
            Map<Material, Integer> heldByMaterial = mode == MatchMode.TOTAL ? null : new HashMap<>();
            int held = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack == null || !materials.contains(stack.getType())) continue;
                if (potionEffect != null && !potionEffect.equals(basePotionEffect(stack))) continue;
                if (mode != MatchMode.TOTAL) {
                    int materialTotal = heldByMaterial.merge(stack.getType(), stack.getAmount(), Integer::sum);
                    if (mode == MatchMode.SINGLE && materialTotal >= count) return true;
                    continue;
                }
                held += stack.getAmount();
                if (held >= count) return true;
            }
            return mode == MatchMode.ALL && materials.stream()
                    .allMatch(material -> heldByMaterial.getOrDefault(material, 0) >= count);
        }

        private static String basePotionEffect(ItemStack stack) {
            if (!(stack.getItemMeta() instanceof PotionMeta meta) || meta.getBasePotionType() == null) return null;
            return meta.getBasePotionType().name().toLowerCase(Locale.ROOT)
                    .replaceFirst("^(strong|long)_", "");
        }
    }

    private final class EventObjective implements Objective {
        private final int cellIndex;
        private final String trigger;
        private final String param;
        private final int count;
        private final Set<Material> members;
        private final Set<String> subjects;

        private EventObjective(int cellIndex, String trigger, String param, int count,
                               Set<Material> members, Set<String> subjects) {
            this.cellIndex = cellIndex;
            this.trigger = trigger;
            this.param = param;
            this.count = count;
            this.members = members;
            this.subjects = subjects;
        }

        @Override
        public int cellIndex() {
            return cellIndex;
        }

        boolean pollable() {
            return !SIGNAL_TRIGGERS.contains(trigger);
        }

        @Override
        public boolean matches(Player player, int ignored) {
            UUID playerId = player.getUniqueId();
            return switch (trigger) {
                case "wear" -> "CHAIN".equalsIgnoreCase(param) ? wearsAny(player, param) : wearsFull(player, param);
                case "wear_full_enchanted" -> wearsFullEnchanted(player);
                case "wear_dyed" -> count >= 4
                        ? wearsFullDyedLeatherDistinct(player) : distinctDyedLeatherColors(player) >= count;
                case "wear_duration" -> wearDurationMet(player, playerId, param, count);
                case "effect" -> hasEffect(player, param);
                case "effect_at_once" -> player.getActivePotionEffects().size() >= count;
                case "reach_level" -> player.getLevel() >= parseIntOr(param, Integer.MAX_VALUE);
                case "reach" -> atReach(player, param);
                case "hunger_empty" -> player.getFoodLevel() == 0;
                case "spy" -> spyOn(player, param);
                case "unique_collect" -> distinctMembersHeld(player, members) >= count;
                case "all_collect" -> hasAllMembers(player, members);
                case "stack_of_64" -> hasStackOf64(player);
                case "fill_inventory_unique" -> fillInventoryUniqueMet(player);
                case "craft_unique" -> eventProgress.distinctCount(playerId, "craft_unique") >= count;
                case "eat_unique" -> eventProgress.distinctCount(playerId, "eat_unique") >= count;
                case "eat_all" -> eventProgress.distinctCount(playerId, "eat_all:" + param) >= count;
                case "breed_unique" -> eventProgress.distinctCount(playerId, "breed_unique") >= count;
                case "leash_unique" -> distinctLeashedSpecies(player) >= count;
                case "compost_unique" -> eventProgress.distinctCount(playerId, "compost_unique") >= count;
                case "kill_family" -> eventProgress.count(playerId, "kill_family:" + param) >= count;
                case "kill_unique" -> eventProgress.distinctCount(playerId, "kill_unique:" + param) >= count;
                case "visit_biomes" -> visitBiomesMet(player, playerId, param, count, subjects);
                case "advancement_count" -> eventProgress.count(playerId, "advancement_count") >= count;
                case "spy_unique" -> spyUniqueMet(player, playerId, count);
                default -> false;
            };
        }
    }

    private record AdvancementObjective(int cellIndex, String key, Advancement advancement) implements Objective {
        @Override
        public boolean matches(Player player, int ignored) {
            return false;
        }
    }

    private record StatisticObjective(int cellIndex, Statistic statistic, Material material,
                                      EntityType entity, int target) implements Objective {
        @Override
        public boolean matches(Player player, int baseline) {
            return read(player) - baseline >= target;
        }

        int read(Player player) {
            try {
                if (material != null) {
                    int value = player.getStatistic(statistic, material);
                    Material variant = statistic == Statistic.MINE_BLOCK ? oreVariant(material) : null;
                    return variant == null ? value : value + player.getStatistic(statistic, variant);
                }
                if (entity != null) return player.getStatistic(statistic, entity);
                return player.getStatistic(statistic);
            } catch (IllegalArgumentException invalidStatistic) {
                return 0;
            }
        }

        private static Material oreVariant(Material material) {
            return switch (material) {
                case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
                case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
                case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
                case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
                case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
                case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
                case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
                case EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
                case DEEPSLATE_COAL_ORE -> Material.COAL_ORE;
                case DEEPSLATE_IRON_ORE -> Material.IRON_ORE;
                case DEEPSLATE_COPPER_ORE -> Material.COPPER_ORE;
                case DEEPSLATE_GOLD_ORE -> Material.GOLD_ORE;
                case DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE_ORE;
                case DEEPSLATE_LAPIS_ORE -> Material.LAPIS_ORE;
                case DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND_ORE;
                case DEEPSLATE_EMERALD_ORE -> Material.EMERALD_ORE;
                default -> null;
            };
        }
    }

    private static final Map<String, List<Material>> WEAR_FAMILIES = Map.of(
            "LEATHER", List.of(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
            "IRON", List.of(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            "GOLDEN", List.of(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS),
            "DIAMOND", List.of(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
            "COPPER", List.of(Material.COPPER_HELMET, Material.COPPER_CHESTPLATE, Material.COPPER_LEGGINGS, Material.COPPER_BOOTS),
            "CHAIN", List.of(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS));

    private boolean wearDurationMet(Player player, UUID playerId, String param, int minutes) {
        boolean active = "CARVED_PUMPKIN".equalsIgnoreCase(param)
                && player.getInventory().getHelmet() != null
                && player.getInventory().getHelmet().getType() == Material.CARVED_PUMPKIN;
        return eventProgress.observeElapsed(playerId, "wear_duration:" + param, active) >= minutes * 60_000L;
    }

    private static boolean wearsAny(Player player, String family) {
        List<Material> pieces = WEAR_FAMILIES.get(family.toUpperCase(Locale.ROOT));
        if (pieces == null) return false;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && pieces.contains(item.getType())) return true;
        }
        return false;
    }

    private static boolean wearsFull(Player player, String family) {
        List<Material> pieces = WEAR_FAMILIES.get(family.toUpperCase(Locale.ROOT));
        if (pieces == null) return false;
        Set<Material> needed = EnumSet.copyOf(pieces);
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || !needed.remove(item.getType())) return false;
        }
        return needed.isEmpty();
    }

    private static boolean wearsFullEnchanted(Player player) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) return false;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasEnchants()) return false;
        }
        return true;
    }

    private static int distinctDyedLeatherColors(Player player) {
        Set<Integer> colors = new java.util.HashSet<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getItemMeta() instanceof LeatherArmorMeta meta && meta.isDyed()) {
                colors.add(meta.getColor().asRGB());
            }
        }
        return colors.size();
    }

    private static boolean wearsFullDyedLeatherDistinct(Player player) {
        Set<Integer> colors = new java.util.HashSet<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || !(item.getItemMeta() instanceof LeatherArmorMeta meta) || !meta.isDyed()
                    || !colors.add(meta.getColor().asRGB())) return false;
        }
        return colors.size() == 4;
    }

    private static boolean hasEffect(Player player, String effect) {
        PotionEffectType type = PotionEffectType.getByName(effect.toUpperCase(Locale.ROOT));
        return type != null && player.hasPotionEffect(type);
    }

    private static boolean atReach(Player player, String place) {
        World world = player.getWorld();
        Location location = player.getLocation();
        return switch (place.toUpperCase(Locale.ROOT)) {
            case "BEDROCK" -> location.getY() < world.getMinHeight() + 10.0
                    && location.clone().add(0, -1, 0).getBlock().getType() == Material.BEDROCK;
            case "HEIGHT_LIMIT" -> location.getY() >= world.getMaxHeight();
            case "NETHER_ROOF" -> world.getEnvironment() == World.Environment.NETHER && location.getY() >= 128.0;
            default -> false;
        };
    }

    private static boolean fillInventoryUniqueMet(Player player) {
        Set<Material> distinct = EnumSet.noneOf(Material.class);
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents.length == 0) return false;
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || !distinct.add(item.getType())) return false;
        }
        return true;
    }

    private static int distinctLeashedSpecies(Player player) {
        Set<EntityType> species = EnumSet.noneOf(EntityType.class);
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 12, 12, 12)) {
            if (entity instanceof LivingEntity living && living.isLeashed() && living.getLeashHolder() == player) {
                species.add(entity.getType());
            }
        }
        return species.size();
    }

    private boolean visitBiomesMet(Player player, UUID playerId, String param, int count, Set<String> subjects) {
        String biome = player.getLocation().getBlock().getBiome().getKey().getKey();
        if (subjects.contains("BIOME=" + biome) || subjects.contains("BIOME=minecraft:" + biome)) {
            eventProgress.recordDistinct(playerId, "visit_biomes:" + param, biome);
        }
        return eventProgress.distinctCount(playerId, "visit_biomes:" + param) >= count;
    }

    private static int distinctMembersHeld(Player player, Set<Material> members) {
        Set<Material> distinct = EnumSet.noneOf(Material.class);
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && members.contains(item.getType())) distinct.add(item.getType());
        }
        return distinct.size();
    }

    private static boolean hasAllMembers(Player player, Set<Material> members) {
        if (members.isEmpty()) return false;
        Set<Material> held = EnumSet.noneOf(Material.class);
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) held.add(item.getType());
        }
        return held.containsAll(members);
    }

    private static boolean hasStackOf64(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getAmount() >= 64) return true;
        }
        return false;
    }

    private static boolean spyOn(Player player, String entityType) {
        if (!usingSpyglass(player)) return false;
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 64,
                entity -> entity instanceof Mob && entity.getType().name().equalsIgnoreCase(entityType)
                        && player.hasLineOfSight(entity));
        return result != null && result.getHitEntity() != null;
    }

    private boolean spyUniqueMet(Player player, UUID playerId, int target) {
        if (!usingSpyglass(player)) return false;
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 64,
                entity -> entity instanceof Mob && entity != player && player.hasLineOfSight(entity));
        if (result == null || result.getHitEntity() == null) return false;
        eventProgress.recordDistinct(playerId, "spy_unique", result.getHitEntity().getType().name());
        return eventProgress.distinctCount(playerId, "spy_unique") >= target;
    }

    private static boolean usingSpyglass(Player player) {
        return player.isHandRaised() && (player.getInventory().getItemInMainHand().getType() == Material.SPYGLASS
                || player.getInventory().getItemInOffHand().getType() == Material.SPYGLASS);
    }

    private static int parseIntOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class EventProgress {
        private final Map<String, Map<UUID, Set<String>>> distinct = new ConcurrentHashMap<>();
        private final Map<String, Map<UUID, Integer>> counts = new ConcurrentHashMap<>();
        private final Map<String, Map<UUID, Long>> elapsedMillis = new ConcurrentHashMap<>();
        private final Map<String, Map<UUID, Long>> observedAt = new ConcurrentHashMap<>();

        void recordDistinct(UUID playerId, String bucket, String value) {
            distinct.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(value);
        }

        int distinctCount(UUID playerId, String bucket) {
            return distinct.getOrDefault(bucket, Map.of()).getOrDefault(playerId, Set.of()).size();
        }

        void increment(UUID playerId, String bucket) {
            counts.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>()).merge(playerId, 1, Integer::sum);
        }

        int count(UUID playerId, String bucket) {
            return counts.getOrDefault(bucket, Map.of()).getOrDefault(playerId, 0);
        }

        long observeElapsed(UUID playerId, String bucket, boolean active) {
            Map<UUID, Long> observations = observedAt.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>());
            long now = System.nanoTime();
            if (!active) {
                observations.remove(playerId);
                return elapsedMillis.getOrDefault(bucket, Map.of()).getOrDefault(playerId, 0L);
            }
            Long previous = observations.put(playerId, now);
            if (previous == null) return elapsedMillis.getOrDefault(bucket, Map.of()).getOrDefault(playerId, 0L);
            return elapsedMillis.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>())
                    .merge(playerId, Math.max(0L, now - previous) / 1_000_000L, Long::sum);
        }
    }
}
