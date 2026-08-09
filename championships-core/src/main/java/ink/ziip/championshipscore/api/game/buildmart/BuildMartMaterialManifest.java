package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.integration.worldedit.WorldEditManager;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Generated, read-only material inventory for Build Mart resource zones. The live map configuration remains
 * the source of truth for gameplay; this file is an inspection artifact for comparing resource coverage with
 * the material requirements of the blueprint pool.
 */
public final class BuildMartMaterialManifest {
    private static final int VERSION = 2;

    private BuildMartMaterialManifest() {
    }

    /**
     * Synchronizes the manifest with configured snapshot files. Existing version-2 entries are reused while
     * their snapshot fingerprint and bounds remain unchanged; only new or replaced schematics are read.
     * Version-1 manifests are rebuilt once so live-world edits captured by the old implementation are discarded.
     */
    public static boolean write(@NotNull ChampionshipsCore plugin, @NotNull BuildMartConfig config) {
        Map<UUID, ZoneInventory> cached = readCachedInventories(config);
        List<ZoneInventory> inventories = new ArrayList<>();
        for (BuildMartMaterialZone zone : config.getMaterialZones()) {
            File snapshot = config.getMaterialZoneSnapshotFile(zone);
            ZoneInventory inventory = cached.get(zone.snapshotId());
            if (inventory == null || !inventory.matches(zone, snapshot)) {
                try {
                    inventory = scanSnapshot(plugin, zone, snapshot);
                } catch (Exception exception) {
                    warn(plugin, config, "无法读取材料区快照=" + snapshot.getName()
                            + " | " + exception.getMessage());
                    return false;
                }
            }
            inventories.add(inventory);
        }

        Map<String, Long> totalMaterials = new TreeMap<>();
        Map<String, Long> totalBlockData = new TreeMap<>();
        long totalVolume = 0L;
        long totalNonAir = 0L;
        for (ZoneInventory inventory : inventories) {
            totalVolume += inventory.zone().volume();
            totalNonAir += inventory.nonAirBlocks();
            merge(totalMaterials, inventory.materials());
            merge(totalBlockData, inventory.blockData());
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().indent(2);
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("version", VERSION);
        marker.put("generated-by", "ChampionshipsCore");
        marker.put("read-only", true);
        yaml.set("dont-edit-this", marker);
        yaml.set("map", config.getAreaName());
        yaml.set("world", config.getConfiguredWorld());
        yaml.set("generated-at", Instant.now().toString());
        yaml.set("zones", inventories.stream().map(BuildMartMaterialManifest::zoneMap).toList());

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("zone-count", inventories.size());
        totals.put("volume", totalVolume);
        totals.put("non-air-blocks", totalNonAir);
        totals.put("materials", totalMaterials);
        totals.put("block-data", totalBlockData);
        yaml.set("totals", totals);

        File file = config.getMaterialManifestFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            warn(plugin, config, "无法创建清单目录=" + parent);
            return false;
        }
        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent.toPath(), file.getName() + ".", ".tmp");
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            warn(plugin, config, "无法写入清单=" + file.getName() + " | " + exception.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // The temporary file is best-effort cleanup; the next save uses a new unique path.
                }
            }
        }
    }

    /** Reads each zone's most common non-air material from the generated inspection manifest. */
    public static @NotNull Map<UUID, Material> readDominantMaterials(@NotNull BuildMartConfig config) {
        File file = config.getMaterialManifestFile();
        if (!file.isFile()) return Map.of();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<UUID, Material> result = new LinkedHashMap<>();
        for (Map<?, ?> row : yaml.getMapList("zones")) {
            UUID snapshotId = parseUuid(row.get("snapshot-id"));
            if (snapshotId == null || !(row.get("materials") instanceof Map<?, ?> materials)) continue;

            Material dominant = null;
            String dominantKey = null;
            long dominantCount = Long.MIN_VALUE;
            for (Map.Entry<?, ?> entry : materials.entrySet()) {
                if (!(entry.getValue() instanceof Number number)) continue;
                String materialKey = String.valueOf(entry.getKey());
                Material material = Material.matchMaterial(materialKey);
                long count = number.longValue();
                if (material == null || material.isAir() || count < 1L) continue;
                if (count > dominantCount || (count == dominantCount
                        && (dominantKey == null || materialKey.compareTo(dominantKey) < 0))) {
                    dominant = material;
                    dominantKey = materialKey;
                    dominantCount = count;
                }
            }
            if (dominant != null) result.put(snapshotId, dominant);
        }
        return Map.copyOf(result);
    }

    private static @NotNull ZoneInventory scanSnapshot(@NotNull ChampionshipsCore plugin,
                                                        @NotNull BuildMartMaterialZone zone,
                                                        @NotNull File snapshot) throws Exception {
        WorldEditManager.SchematicBlockInventory scanned = plugin.getWorldEditManager()
                .readSchematicBlockInventory(snapshot);
        if (scanned.volume() != zone.volume()) {
            throw new IllegalStateException("体积不一致，配置=" + zone.volume() + "，快照=" + scanned.volume());
        }
        return new ZoneInventory(zone, scanned.nonAirBlocks(), scanned.materials(), scanned.blockData(),
                snapshot.length(), snapshot.lastModified());
    }

    private static @NotNull Map<UUID, ZoneInventory> readCachedInventories(@NotNull BuildMartConfig config) {
        File file = config.getMaterialManifestFile();
        if (!file.isFile()) return Map.of();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("dont-edit-this.version", -1) != VERSION) return Map.of();

        Map<UUID, ZoneInventory> result = new LinkedHashMap<>();
        for (Map<?, ?> row : yaml.getMapList("zones")) {
            UUID snapshotId = parseUuid(row.get("snapshot-id"));
            Vector pos1 = vector(row.get("pos1"));
            Vector pos2 = vector(row.get("pos2"));
            Number nonAir = number(row.get("non-air-blocks"));
            Number snapshotSize = number(row.get("snapshot-size"));
            Number snapshotModified = number(row.get("snapshot-last-modified"));
            Map<String, Long> materials = counts(row.get("materials"));
            Map<String, Long> blockData = counts(row.get("block-data"));
            if (snapshotId == null || pos1 == null || pos2 == null || nonAir == null
                    || snapshotSize == null || snapshotModified == null
                    || materials == null || blockData == null) continue;
            BuildMartMaterialZone zone = new BuildMartMaterialZone(snapshotId, pos1, pos2);
            result.put(snapshotId, new ZoneInventory(zone, nonAir.longValue(), materials, blockData,
                    snapshotSize.longValue(), snapshotModified.longValue()));
        }
        return result;
    }

    private static Map<String, Object> zoneMap(@NotNull ZoneInventory inventory) {
        BuildMartMaterialZone zone = inventory.zone();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("snapshot-id", zone.snapshotId().toString());
        row.put("pos1", vectorMap(zone.pos1()));
        row.put("pos2", vectorMap(zone.pos2()));
        row.put("volume", zone.volume());
        row.put("non-air-blocks", inventory.nonAirBlocks());
        row.put("snapshot-size", inventory.snapshotSize());
        row.put("snapshot-last-modified", inventory.snapshotLastModified());
        row.put("materials", inventory.materials());
        row.put("block-data", inventory.blockData());
        return row;
    }

    private static Map<String, Object> vectorMap(@NotNull Vector vector) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("x", vector.getX());
        row.put("y", vector.getY());
        row.put("z", vector.getZ());
        return row;
    }

    private static @Nullable Vector vector(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Number x = number(map.get("x"));
        Number y = number(map.get("y"));
        Number z = number(map.get("z"));
        return x == null || y == null || z == null ? null
                : new Vector(x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    private static @Nullable Number number(Object value) {
        return value instanceof Number number ? number : null;
    }

    private static @Nullable Map<String, Long> counts(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Map<String, Long> result = new TreeMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Number number) || number.longValue() < 0L) return null;
            result.put(String.valueOf(entry.getKey()), number.longValue());
        }
        return Map.copyOf(result);
    }

    private static void merge(@NotNull Map<String, Long> target, @NotNull Map<String, Long> source) {
        source.forEach((key, value) -> target.merge(key, value, Long::sum));
    }

    private static UUID parseUuid(Object value) {
        if (!(value instanceof String text)) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void warn(@NotNull ChampionshipsCore plugin, @NotNull BuildMartConfig config,
                             @NotNull String message) {
        plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, config.getAreaName(),
                "材料区", "清单", message));
    }

    private record ZoneInventory(@NotNull BuildMartMaterialZone zone, long nonAirBlocks,
                                 @NotNull Map<String, Long> materials,
                                 @NotNull Map<String, Long> blockData,
                                 long snapshotSize, long snapshotLastModified) {
        private boolean matches(@NotNull BuildMartMaterialZone configured, @NotNull File snapshot) {
            return zone.minX() == configured.minX() && zone.maxX() == configured.maxX()
                    && zone.minY() == configured.minY() && zone.maxY() == configured.maxY()
                    && zone.minZ() == configured.minZ() && zone.maxZ() == configured.maxZ()
                    && snapshot.isFile() && snapshotSize == snapshot.length()
                    && snapshotLastModified == snapshot.lastModified();
        }
    }
}
