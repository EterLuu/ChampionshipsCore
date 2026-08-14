package ink.ziip.championshipscore.api.game.buildmart.blueprint;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialIsland;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartCopperPolicy;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure, reusable structural/difficulty/resource audit for one Build Mart blueprint. */
public final class BuildMartBlueprintAuditor {
    private static final Set<String> DIRECTION_KEYS = Set.of(
            "axis", "face", "facing", "half", "hinge", "orientation", "rotation", "shape", "type");
    private static final Set<String> COMPLEX_KEYS = Set.of(
            "axis", "face", "facing", "half", "hinge", "orientation", "rotation", "shape", "type",
            "attached", "hanging", "in_wall", "open", "part", "side_chain", "signal_fire", "waterlogged");
    private static final List<String> WOODS = List.of("pale_oak", "dark_oak", "mangrove", "cherry",
            "spruce", "birch", "jungle", "acacia", "oak", "bamboo", "crimson", "warped");
    private static final List<String> STONE_BASES = List.of("polished_blackstone_bricks", "mossy_stone_bricks",
            "mossy_cobblestone", "deepslate_bricks", "deepslate_tiles", "polished_deepslate",
            "end_stone_bricks", "prismarine_bricks", "dark_prismarine", "smooth_red_sandstone",
            "red_sandstone", "smooth_sandstone", "sandstone", "smooth_quartz", "quartz_block",
            "polished_andesite", "polished_diorite", "polished_granite", "polished_tuff", "mud_bricks",
            "nether_bricks", "stone_bricks", "blackstone", "cobblestone", "prismarine", "andesite",
            "diorite", "granite", "deepslate", "tuff", "bricks", "stone", "purpur_block");
    private static final List<String> COLORS = List.of("light_blue", "light_gray", "white", "orange",
            "magenta", "yellow", "lime", "pink", "gray", "cyan", "purple", "blue", "brown",
            "green", "red", "black");

    private BuildMartBlueprintAuditor() {
    }

    public static @NotNull Audit audit(@NotNull BuildMartBlueprint blueprint,
                                       @NotNull BuildMartMaterialManifest.AuditInventory inventory) {
        List<BlueprintBlock> blocks = blueprint.getBlocks();
        Set<Position> positions = new HashSet<>();
        Set<Material> materials = new LinkedHashSet<>();
        int stateful = 0;
        int directional = 0;
        int complex = 0;
        int strictConnectable = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int outOfBounds = 0;

        for (BlueprintBlock block : blocks) {
            positions.add(new Position(block.getX(), block.getY(), block.getZ()));
            materials.add(BuildMartCopperPolicy.withoutWax(block.getBlockData().getMaterial()));
            Set<String> keys = propertyKeys(block.getBlockData());
            if (!keys.isEmpty()) stateful++;
            if (!disjoint(keys, DIRECTION_KEYS)) directional++;
            if (!disjoint(keys, COMPLEX_KEYS)) complex++;
            String material = key(block.getBlockData().getMaterial());
            if (material.endsWith("_pane") || material.endsWith("_bars") || material.endsWith("_wall")) {
                strictConnectable++;
            }
            minX = Math.min(minX, block.getX()); maxX = Math.max(maxX, block.getX());
            minY = Math.min(minY, block.getY()); maxY = Math.max(maxY, block.getY());
            minZ = Math.min(minZ, block.getZ()); maxZ = Math.max(maxZ, block.getZ());
            if (block.getX() < 0 || block.getX() > 6 || block.getY() < 0 || block.getY() > 6
                    || block.getZ() < 0 || block.getZ() > 6) outOfBounds++;
        }

        int sizeX = blocks.isEmpty() ? 0 : maxX - minX + 1;
        int sizeY = blocks.isEmpty() ? 0 : maxY - minY + 1;
        int sizeZ = blocks.isEmpty() ? 0 : maxZ - minZ + 1;
        int components = components(positions);

        int direct = 0;
        int craftable = 0;
        Set<Material> uncovered = new LinkedHashSet<>();
        Set<BuildMartMaterialIsland> islands = new LinkedHashSet<>();
        if (inventory.available()) {
            Set<Material> available = inventory.materials().keySet();
            for (Material material : materials) {
                Set<Material> sources;
                if (available.contains(material)) {
                    direct++;
                    sources = Set.of(material);
                } else {
                    sources = conversionSources(material, available);
                    if (sources == null) {
                        uncovered.add(material);
                        continue;
                    }
                    craftable++;
                }
                for (Material source : sources) {
                    islands.addAll(inventory.islandsByMaterial().getOrDefault(source, Set.of()));
                }
            }
        }

        double raw = blocks.size() * 0.55
                + Math.max(0, materials.size() - 1) * 2.0
                + stateful * 0.12 + directional * 0.30 + complex * 0.18 + strictConnectable * 0.18
                + Math.max(0, sizeY - 1) * 2.5 + Math.max(0, components - 1) * 1.5
                + Math.max(0, islands.size() - 1) * 2.8;
        double score = Math.round(Math.min(100.0, raw * 100.0 / 115.0) * 10.0) / 10.0;
        int suggested = suggestedStars(score);

        List<String> warnings = new ArrayList<>();
        int duplicates = blocks.size() - positions.size();
        if (duplicates > 0) warnings.add("重复坐标 " + duplicates + " 个");
        if (outOfBounds > 0) warnings.add("7×7×7 范围外方块 " + outOfBounds + " 个");
        if (strictConnectable > 0) warnings.add("严格自动连接方块 " + strictConnectable + " 个");
        if (!inventory.available()) warnings.add("材料清单不存在，未检查覆盖");
        else if (!uncovered.isEmpty()) warnings.add("材料区不能完整覆盖");
        if (blueprint.getStars() != suggested) warnings.add("当前星级与建议星级不一致");

        return new Audit(blueprint.getId(), blueprint.getDisplayName(), blueprint.getStars(), suggested, score,
                blocks.size(), materials.size(), sizeX, sizeY, sizeZ, components, stateful, directional, complex,
                strictConnectable, islands, direct, craftable, uncovered, inventory.available(), warnings);
    }

    public static int suggestedStars(double score) {
        if (score < 20.0) return 1;
        if (score < 40.0) return 2;
        if (score < 60.0) return 3;
        if (score < 80.0) return 4;
        return 5;
    }

    private static Set<String> propertyKeys(BlockData data) {
        String value = data.getAsString();
        int open = value.indexOf('[');
        int close = value.lastIndexOf(']');
        if (open < 0 || close <= open) return Set.of();
        Set<String> keys = new HashSet<>();
        for (String property : value.substring(open + 1, close).split(",")) {
            int equals = property.indexOf('=');
            if (equals > 0 && !property.substring(0, equals).equals("age")) {
                keys.add(property.substring(0, equals));
            }
        }
        return keys;
    }

    private static boolean disjoint(Set<String> left, Set<String> right) {
        for (String value : left) if (right.contains(value)) return false;
        return true;
    }

    private static int components(Set<Position> positions) {
        Set<Position> remaining = new HashSet<>(positions);
        int count = 0;
        ArrayDeque<Position> queue = new ArrayDeque<>();
        while (!remaining.isEmpty()) {
            count++;
            Position first = remaining.iterator().next();
            remaining.remove(first);
            queue.add(first);
            while (!queue.isEmpty()) {
                Position current = queue.removeFirst();
                for (Position neighbour : current.neighbours()) {
                    if (remaining.remove(neighbour)) queue.addLast(neighbour);
                }
            }
        }
        return count;
    }

    private static Set<Material> conversionSources(Material material, Set<Material> available) {
        String name = key(material);
        Set<Material> wood = woodSources(name, available);
        if (wood != null) return wood;
        Set<Material> stone = stoneSources(name, available);
        if (stone != null) return stone;

        if (name.endsWith("_stained_glass_pane")) return required(available, name.substring(0, name.length() - 5));
        if (name.equals("glass_pane")) return required(available, "glass");
        if (name.endsWith("_carpet")) return required(available,
                name.substring(0, name.length() - "_carpet".length()) + "_wool");
        if (name.endsWith("_banner") || name.endsWith("_wall_banner") || name.endsWith("_bed")) {
            for (String color : COLORS) if (name.startsWith(color + "_")) {
                return required(available, color + "_wool", "oak_log");
            }
        }
        Map<String, List<String>> fixed = fixedConversions();
        if (fixed.containsKey(name)) return required(available, fixed.get(name).toArray(String[]::new));
        if (name.startsWith("potted_")) return required(available, "clay", name.substring(7));
        if (name.contains("copper") || name.endsWith("lightning_rod")) return copperSources(name, available);
        if (name.equals("dirt_path")) return firstAvailable(available, "dirt", "grass_block");
        if (name.equals("carved_pumpkin")) return required(available, "pumpkin");
        if (name.equals("moss_carpet")) return required(available, "moss_block");
        if (name.startsWith("chiseled_") || name.startsWith("cracked_")) {
            return stoneSources(name.replaceFirst("^(chiseled|cracked)_", ""), available);
        }
        return null;
    }

    /** Exact match-time copper conversions; oxidation over time is deliberately not considered obtainable. */
    static Set<Material> copperSources(String material, Set<Material> available) {
        Material exact = Material.matchMaterial(material);
        if (exact != null && available.contains(exact)) return Set.of(exact);
        if (material.startsWith("waxed_")) {
            String unwaxed = material.substring("waxed_".length());
            String stage = copperStage(unwaxed);
            if (stage != null && isStonecutCopper(unwaxed, stage)) {
                String waxedBase = stage.isEmpty() ? "waxed_copper_block" : "waxed_" + stage + "copper";
                Set<Material> source = required(available, waxedBase);
                if (source != null) return source;
            }
            if (!available.contains(Material.HONEYCOMB)) return null;
            Set<Material> unwaxedSources = copperSources(unwaxed, available);
            if (unwaxedSources == null) return null;
            Set<Material> sources = new LinkedHashSet<>(unwaxedSources);
            sources.add(Material.HONEYCOMB);
            return sources;
        }

        String stage = copperStage(material);
        if (stage != null && isStonecutCopper(material, stage)) {
            String base = stage.isEmpty() ? "copper_block" : stage + "copper";
            Set<Material> source = required(available, base);
            if (source != null) return source;
        }

        // Only pristine forms have immediate crafting recipes from copper ingots. Exposed/weathered/
        // oxidized special parts require real-time oxidation and are therefore not match-time conversions.
        if (material.equals("copper_bars") || material.equals("copper_chain")
                || material.equals("copper_trapdoor") || material.equals("copper_door")
                || material.equals("lightning_rod")) {
            return required(available, "copper_block");
        }
        if (material.equals("copper_chest")) return required(available, "copper_block", "oak_log");
        if (material.equals("copper_lantern")) {
            return required(available, "copper_block", "coal_block", "oak_log");
        }
        return null;
    }

    /** "", "exposed_", "weathered_", "oxidized_", or null when this is not a copper family name. */
    private static String copperStage(String material) {
        if (material.equals("copper_block") || material.startsWith("copper_")
                || material.startsWith("cut_copper") || material.startsWith("chiseled_copper")) return "";
        for (String stage : List.of("exposed_", "weathered_", "oxidized_")) {
            if (material.startsWith(stage + "copper") || material.startsWith(stage + "cut_copper")
                    || material.startsWith(stage + "chiseled_copper")) return stage;
        }
        return null;
    }

    /** Parts exposed by vanilla stonecutting from the same oxidation/wax stage's full copper block. */
    private static boolean isStonecutCopper(String material, String stage) {
        String prefix = stage;
        return material.equals(prefix + "cut_copper") || material.equals(prefix + "cut_copper_slab")
                || material.equals(prefix + "cut_copper_stairs")
                || material.equals(prefix + "chiseled_copper") || material.equals(prefix + "copper_grate");
    }

    private static Set<Material> woodSources(String material, Set<Material> available) {
        for (String wood : WOODS) {
            if (!material.startsWith(wood + "_") && !material.startsWith("stripped_" + wood + "_")) continue;
            return wood.equals("bamboo") ? firstAvailable(available, "bamboo_block", "bamboo")
                    : required(available, wood + (wood.equals("crimson") || wood.equals("warped") ? "_stem" : "_log"));
        }
        return null;
    }

    private static Set<Material> stoneSources(String material, Set<Material> available) {
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("polished_blackstone", "blackstone"),
                Map.entry("mossy_stone_brick", "stone_bricks"),
                Map.entry("deepslate_brick", "deepslate"), Map.entry("deepslate_tile", "deepslate"),
                Map.entry("end_stone_brick", "end_stone"), Map.entry("quartz", "quartz_block"),
                Map.entry("purpur", "purpur_block"), Map.entry("mud_brick", "mud_bricks"),
                Map.entry("nether_brick", "nether_bricks"), Map.entry("brick", "bricks"),
                Map.entry("smooth_stone", "stone"));
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (material.equals(alias.getKey()) || material.startsWith(alias.getKey() + "_")) {
                Set<Material> source = required(available, alias.getValue());
                if (source != null) return source;
            }
        }
        Map<String, String> fallback = Map.ofEntries(
                Map.entry("smooth_quartz", "quartz_block"), Map.entry("smooth_red_sandstone", "red_sand"),
                Map.entry("red_sandstone", "red_sand"), Map.entry("smooth_sandstone", "sand"),
                Map.entry("sandstone", "sand"), Map.entry("polished_blackstone_bricks", "blackstone"),
                Map.entry("mossy_cobblestone", "cobblestone"), Map.entry("mossy_stone_bricks", "stone_bricks"),
                Map.entry("deepslate_bricks", "deepslate"), Map.entry("deepslate_tiles", "deepslate"),
                Map.entry("polished_deepslate", "deepslate"), Map.entry("end_stone_bricks", "end_stone"),
                Map.entry("polished_andesite", "andesite"), Map.entry("polished_diorite", "diorite"),
                Map.entry("polished_granite", "granite"), Map.entry("polished_tuff", "tuff"));
        for (String base : STONE_BASES) {
            if (!material.equals(base) && !material.startsWith(base + "_")) continue;
            Set<Material> direct = required(available, base);
            if (direct != null) return direct;
            String source = fallback.get(base);
            return source == null ? null : required(available, source);
        }
        return null;
    }

    private static Map<String, List<String>> fixedConversions() {
        Map<String, List<String>> values = new HashMap<>();
        values.put("anvil", List.of("iron_block")); values.put("cauldron", List.of("iron_block"));
        values.put("hopper", List.of("iron_block", "oak_log")); values.put("iron_bars", List.of("iron_block"));
        values.put("iron_chain", List.of("iron_block")); values.put("iron_trapdoor", List.of("iron_block"));
        values.put("heavy_weighted_pressure_plate", List.of("iron_block"));
        values.put("light_weighted_pressure_plate", List.of("gold_block"));
        values.put("lantern", List.of("iron_block", "coal_block", "oak_log"));
        values.put("rail", List.of("iron_block", "oak_log"));
        values.put("tripwire_hook", List.of("iron_block", "oak_log"));
        values.put("lever", List.of("stone", "oak_log"));
        values.put("redstone_torch", List.of("redstone_block", "oak_log"));
        values.put("redstone_lamp", List.of("redstone_block", "glowstone"));
        values.put("comparator", List.of("redstone_block", "quartz_block", "stone"));
        values.put("note_block", List.of("redstone_block", "oak_log"));
        values.put("jukebox", List.of("diamond_block", "oak_log"));
        values.put("target", List.of("redstone_block", "hay_block"));
        values.put("crafter", List.of("redstone_block", "iron_block", "stone", "oak_log"));
        values.put("furnace", List.of("stone")); values.put("blast_furnace", List.of("stone", "iron_block"));
        values.put("smoker", List.of("stone", "oak_log")); values.put("grindstone", List.of("stone", "oak_log"));
        values.put("stonecutter", List.of("stone", "iron_block")); values.put("campfire", List.of("coal_block", "oak_log"));
        values.put("barrel", List.of("oak_log")); values.put("chest", List.of("oak_log"));
        values.put("crafting_table", List.of("oak_log")); values.put("composter", List.of("oak_log"));
        values.put("ladder", List.of("oak_log")); values.put("flower_pot", List.of("clay"));
        values.put("decorated_pot", List.of("clay"));
        values.put("piston_head", List.of("stone", "oak_log", "iron_block", "redstone_block"));
        values.put("sticky_piston", List.of("stone", "oak_log", "iron_block", "redstone_block", "slime_block"));
        return values;
    }

    private static Set<Material> required(Set<Material> available, String... names) {
        Set<Material> result = new LinkedHashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material == null || !available.contains(material)) return null;
            result.add(material);
        }
        return result;
    }

    private static Set<Material> firstAvailable(Set<Material> available, String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null && available.contains(material)) return Set.of(material);
        }
        return null;
    }

    private static String key(Material material) {
        return material.getKey().getKey().toLowerCase(Locale.ROOT);
    }

    private record Position(int x, int y, int z) {
        List<Position> neighbours() {
            return List.of(new Position(x + 1, y, z), new Position(x - 1, y, z),
                    new Position(x, y + 1, z), new Position(x, y - 1, z),
                    new Position(x, y, z + 1), new Position(x, y, z - 1));
        }
    }

    public record Audit(@NotNull String id, @NotNull String name, int configuredStars, int suggestedStars,
                        double score, int blocks, int uniqueMaterials, int sizeX, int sizeY, int sizeZ,
                        int components, int statefulBlocks, int directionalBlocks, int complexStateBlocks,
                        int strictConnectableBlocks, @NotNull Set<BuildMartMaterialIsland> materialIslands,
                        int directMaterials, int craftableMaterials, @NotNull Set<Material> uncoveredMaterials,
                        boolean coverageChecked, @NotNull List<String> warnings) {
        public Audit {
            materialIslands = materialIslands.stream().sorted(Comparator.comparing(Enum::ordinal))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            uncoveredMaterials = uncoveredMaterials.stream().sorted(Comparator.comparing(BuildMartBlueprintAuditor::key))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            warnings = List.copyOf(warnings);
        }

        public String dimensions() {
            return sizeX + "×" + sizeY + "×" + sizeZ;
        }

        public boolean fullyCovered() {
            return coverageChecked && uncoveredMaterials.isEmpty();
        }
    }
}
