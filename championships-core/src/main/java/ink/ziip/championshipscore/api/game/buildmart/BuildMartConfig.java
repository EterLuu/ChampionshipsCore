package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaLayoutPlanner;
import ink.ziip.championshipscore.api.game.arena.SourceAnchoredRowArenaGrid;
import ink.ziip.championshipscore.api.game.arena.SourceAnchoredRingArenaGrid;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-map Build Mart configuration inside a persistent shared physical world. Geometry is split between
 * the hand-built central resource market and a single base template. Copy 0 is reserved for that template;
 * the playable team bases begin at copy 1 and runtime reset restores only this map's configured regions.
 */
@Getter
@Setter
public class BuildMartConfig extends BaseGameConfig {
    private final String resourceName = "buildmart/areas/area.yml";
    private final String folderName = "buildmart/areas/";

    public BuildMartConfig(@NotNull ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 17;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Bound by the prepare flow before publication; blank in a new draft template. */
    @ConfigOption(path = "world-name", nullable = true)
    private String worldName;

    /** Round duration in seconds. Default 12 minutes. */
    @ConfigOption(path = "timer")
    private int timer = 720;

    /** Preparation countdown before the round starts, in seconds. */
    @ConfigOption(path = "prepare-time")
    private int prepareTime = 10;

    /** Number of playable team bases physically stamped into this map (copies 1..N). */
    @ConfigOption(path = "base-count")
    private int baseCount = 8;

    @ConfigOption(path = "copy-layout.center", nullable = true)
    private Vector copyLayoutCenter;

    /** New maps use ROW. RING remains readable only so an old physical map is never moved implicitly. */
    @ConfigOption(path = "copy-layout.type")
    private String copyLayoutType = "ROW";

    @ConfigOption(path = "copy-layout.source-origin", nullable = true)
    private Vector baseSourceOrigin;

    @ConfigOption(path = "copy-layout.spacing", nullable = true)
    private Integer copyLayoutSpacing;

    @ConfigOption(path = "copy-layout.generated-origin", nullable = true)
    private Vector copyLayoutGeneratedOrigin;

    @ConfigOption(path = "copy-layout.step", nullable = true)
    private Vector copyLayoutStep;

    @ConfigOption(path = "copy-layout.base-size", nullable = true)
    private Vector baseSchematicSize;

    public @NotNull ArenaGrid getBaseGrid() {
        if (isRowLayout() && baseSourceOrigin != null && copyLayoutGeneratedOrigin != null
                && copyLayoutStep != null) {
            return new SourceAnchoredRowArenaGrid(baseSourceOrigin, copyLayoutGeneratedOrigin, copyLayoutStep);
        }
        Vector center = copyLayoutCenter == null ? hubGridCenter() : copyLayoutCenter.clone();
        Vector source = baseSourceOrigin == null ? center.clone() : baseSourceOrigin.clone();
        int spacing = copyLayoutSpacing == null ? BuildMartLayout.SPACING : copyLayoutSpacing;
        return new SourceAnchoredRingArenaGrid(source, center, spacing);
    }

    /**
     * Keeps copy 0 at the source schematic's original minimum corner. ROW maps generate their playable
     * copies east of all configured infrastructure; legacy RING maps retain their physical coordinates.
     */
    public @NotNull ArenaGrid prepareBaseGrid(@NotNull Vector baseOrigin, @NotNull Vector baseSize) {
        if (isRowLayout()) {
            baseSourceOrigin = baseOrigin.clone();
            baseSchematicSize = baseSize.clone();
            copyLayoutGeneratedOrigin = BuildMartRowLayoutPlanner.generatedOrigin(baseOrigin,
                    configuredInfrastructureMaxX(baseOrigin, baseSize));
            copyLayoutStep = BuildMartRowLayoutPlanner.step(baseSize);
            copyLayoutCenter = null;
            copyLayoutSpacing = null;
            return getBaseGrid();
        }
        Vector hubCenter = hubGridCenter();
        copyLayoutCenter = new Vector(hubCenter.getX(), baseOrigin.getY(), hubCenter.getZ());
        baseSourceOrigin = baseOrigin.clone();
        copyLayoutSpacing = ArenaLayoutPlanner.ringSpacing(hubFootprint(), baseSize);
        baseSchematicSize = baseSize.clone();
        return getBaseGrid();
    }

    public boolean isRowLayout() {
        return "ROW".equalsIgnoreCase(copyLayoutType);
    }

    /** New maps opt into row placement before their first schematic is captured. */
    public void useRowLayoutForDraft() {
        copyLayoutType = "ROW";
        copyLayoutCenter = null;
        copyLayoutSpacing = null;
    }

    /** Highest known infrastructure coordinate, used to keep a freshly generated row clear of the hub. */
    public double configuredInfrastructureMaxX(@NotNull Vector baseOrigin, @NotNull Vector baseSize) {
        double max = baseOrigin.getX() + baseSize.getBlockX();
        max = maxX(max, areaPos1, areaPos2, hubPos1, hubPos2);
        max = maxX(max, spectatorSpawnPoint, hubPortalPoint, goldenDisplayPoint, getIntroductionSpawnPoint());
        for (WindZone zone : windZones) max = maxX(max, zone.pos1(), zone.pos2());
        for (BuildMartMaterialZone zone : getMaterialZones()) max = Math.max(max, zone.maxX());
        for (Vector center : materialIslandCenters.values()) max = Math.max(max, center.getX());
        return max;
    }

    private static double maxX(double current, Vector... vectors) {
        double max = current;
        for (Vector vector : vectors) if (vector != null) max = Math.max(max, vector.getX());
        return max;
    }

    private static double maxX(double current, Location... locations) {
        double max = current;
        for (Location location : locations) if (location != null) max = Math.max(max, location.getX());
        return max;
    }

    /** Records copy 0's real selection corner and invalidates copies made from an older template. */
    public void recordBaseTemplateOrigin(@NotNull Vector baseOrigin) {
        baseSourceOrigin = baseOrigin.clone();
        prepareWorldBuilt = false;
        saveOptions();
    }

    /** Legacy fallback for callers that do not have schematic metadata yet. */
    public @NotNull ArenaGrid prepareBaseGrid(@NotNull Vector baseSize) {
        return prepareBaseGrid(hubGridCenter(), baseSize);
    }

    private @NotNull Vector hubGridCenter() {
        if (hubPos1 == null || hubPos2 == null) return BuildMartLayout.HUB.clone();
        Vector min = Vector.getMinimum(hubPos1, hubPos2);
        Vector max = Vector.getMaximum(hubPos1, hubPos2);
        return new Vector((min.getX() + max.getX() + 1.0) / 2.0, min.getY(),
                (min.getZ() + max.getZ() + 1.0) / 2.0);
    }

    private @NotNull Vector hubFootprint() {
        if (hubPos1 == null || hubPos2 == null)
            throw new IllegalStateException("资源大厅边界尚未设置");
        Vector min = Vector.getMinimum(hubPos1, hubPos2);
        Vector max = Vector.getMaximum(hubPos1, hubPos2);
        return max.subtract(min).add(new Vector(1, 1, 1));
    }

    /** Legacy whole-map boundary fields; retained for old configurations but no longer used by gameplay. */
    @ConfigOption(path = "area-pos1", nullable = true)
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2", nullable = true)
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point", nullable = true)
    private Location spectatorSpawnPoint;

    /** Hub bounding box: inside it flight is disabled and block placement is blocked. */
    @ConfigOption(path = "hub-pos1", nullable = true)
    private Vector hubPos1;

    @ConfigOption(path = "hub-pos2", nullable = true)
    private Vector hubPos2;

    /** Landing point reached after entering a team-base portal. */
    @ConfigOption(path = "hub-portal-point", nullable = true)
    private Location hubPortalPoint;

    /** Legacy single wind-vent fields, read only to migrate old maps to {@link #windZones}. */
    private Vector windZonePos1;
    private Vector windZonePos2;

    /** Horizontal/vertical cuboids occupied by wind-vent blocks. Players above any are lifted toward Y=200. */
    private List<WindZone> windZones = List.of();

    /** Persisted centres of the 24 physical material islands, keyed by their stable semantic identity. */
    private Map<BuildMartMaterialIsland, Vector> materialIslandCenters = Map.of();

    public record WindZone(@NotNull Vector pos1, @NotNull Vector pos2) {
        public WindZone {
            pos1 = pos1.clone();
            pos2 = pos2.clone();
        }

        @Override
        public @NotNull Vector pos1() {
            return pos1.clone();
        }

        @Override
        public @NotNull Vector pos2() {
            return pos2.clone();
        }
    }

    /** Returns an immutable snapshot so callers cannot mutate the loaded geometry in place. */
    public @NotNull List<WindZone> getWindZones() {
        return List.copyOf(windZones);
    }

    public void setWindZones(@Nullable List<WindZone> zones) {
        if (zones == null || zones.isEmpty()) {
            windZones = List.of();
            windZonePos1 = null;
            windZonePos2 = null;
            return;
        }
        windZones = List.copyOf(zones);
        WindZone first = windZones.get(0);
        windZonePos1 = first.pos1();
        windZonePos2 = first.pos2();
    }

    /** Anchor where the current golden blueprint is pasted in the hub for players to observe. */
    @ConfigOption(path = "golden-display-point", nullable = true)
    private Location goldenDisplayPoint;

    /** How often (seconds) the golden blueprint is swapped; it stays live for this whole window. */
    @ConfigOption(path = "golden-refresh-seconds")
    private int goldenRefreshSeconds = 120;

    /** Cooldown (ms) on portal triggers to stop the player bouncing back and forth. */
    @ConfigOption(path = "portal-cooldown-millis")
    private long portalCooldownMillis = 1000L;

    /**
     * The single configured 0th-base template, or {@code null} if unconfigured. Read live
     * from the {@code base} section so the per-leaf writes from {@link #setBaseLocation} are never clobbered
     * by {@link #saveOptions()}. Other seats are derived from this via {@link #getSeatBase(int)}.
     */
    @Nullable
    public BuildMartBase getBaseTemplate() {
        if (configuration == null) return null;
        ConfigurationSection section = configuration.getConfigurationSection("base");
        if (section == null) return null;
        return new BuildMartBase(0, section);
    }

    /**
     * Geometry for the playable base assigned to {@code seat} (0-based). The editable template is physical
     * copy 0, so seat 0 resolves to physical copy 1 and is never assigned the template itself.
     */
    @Nullable
    public BuildMartBase getSeatBase(int seat) {
        return resolveMapGeometry().baseForSeat(seat);
    }

    public @NotNull BuildMartMapGeometry resolveMapGeometry() {
        return BuildMartMapGeometry.from(this);
    }

    /** True when {@code location} lies within the configured hub bounding box. */
    public boolean isInHub(@NotNull Location location) {
        return resolveMapGeometry().isInHub(location);
    }

    /**
     * True when a location is inside the shared resource hub or one of the stamped team-base cuboids.
     * Build Mart has no single enclosing arena box: the bases are deliberately separated around the hub.
     */
    public boolean isInPlayableArea(@Nullable Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getConfiguredWorld())) {
            return false;
        }
        if (isInWindColumn(location)) return true;
        if (isInHub(location)) return true;

        Vector size = baseSchematicSize;
        if (size == null) return false;
        int width = Math.max(1, size.getBlockX());
        int height = Math.max(1, size.getBlockY());
        int depth = Math.max(1, size.getBlockZ());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        ArenaGrid grid = getBaseGrid();
        for (int seat = 0; seat < Math.max(0, baseCount); seat++) {
            Vector origin = grid.origin(playableCopyIndex(seat));
            if (x >= origin.getBlockX() && x < origin.getBlockX() + width
                    && y >= origin.getBlockY() && y < origin.getBlockY() + height
                    && z >= origin.getBlockZ() && z < origin.getBlockZ() + depth) {
                return true;
            }
        }
        return false;
    }

    /** True when a player is horizontally above the configured wind vent and has cleared its top face. */
    public boolean isAboveWindZone(@NotNull Location location) {
        if (!isInWindColumn(location)) return false;
        for (WindZone zone : windZones) {
            Vector min = Vector.getMinimum(zone.pos1(), zone.pos2());
            Vector max = Vector.getMaximum(zone.pos1(), zone.pos2());
            if (location.getX() >= min.getX() && location.getX() <= max.getX() + 1.0
                    && location.getZ() >= min.getZ() && location.getZ() <= max.getZ() + 1.0
                    && location.getY() >= max.getY() + 1.0) return true;
        }
        return false;
    }

    /** True when a location is in the vent's vertical column, including the lift path up to Y=200. */
    private boolean isInWindColumn(@NotNull Location location) {
        if (windZones.isEmpty() || location.getWorld() == null
                || !location.getWorld().getName().equals(getConfiguredWorld())) return false;
        for (WindZone zone : windZones) {
            Vector min = Vector.getMinimum(zone.pos1(), zone.pos2());
            Vector max = Vector.getMaximum(zone.pos1(), zone.pos2());
            if (location.getX() >= min.getX() && location.getX() <= max.getX() + 1.0
                    && location.getZ() >= min.getZ() && location.getZ() <= max.getZ() + 1.0
                    && location.getY() >= min.getY()
                    && location.getY() <= BuildMartWindVentPolicy.TOP_Y + 1.0) return true;
        }
        return false;
    }

    /** True when a location is inside the stamped base cuboid for a particular seat. */
    public boolean isInBase(@NotNull Location location, int seat) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(getConfiguredWorld())) return false;
        Vector size = baseSchematicSize;
        if (size == null || seat < 0 || seat >= Math.max(0, baseCount)) return false;
        Vector origin = getBaseGrid().origin(playableCopyIndex(seat));
        return insideCuboid(location, origin, size);
    }

    /** True when a location is inside the physical 0th template cuboid. */
    public boolean isInBaseTemplate(@NotNull Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(getConfiguredWorld())) return false;
        if (baseSchematicSize == null) return false;
        return insideCuboid(location, getBaseGrid().origin(0), baseSchematicSize);
    }

    private static boolean insideCuboid(@NotNull Location location, @NotNull Vector origin, @NotNull Vector size) {
        return location.getX() >= origin.getX() && location.getX() < origin.getX() + Math.max(1, size.getX())
                && location.getY() >= origin.getY() && location.getY() < origin.getY() + Math.max(1, size.getY())
                && location.getZ() >= origin.getZ() && location.getZ() < origin.getZ() + Math.max(1, size.getZ());
    }

    /**
     * Writes a string-serialized location into the {@code base.<key>} template section and persists it.
     * Used by the area set-up commands; the admin configures these standing in seat 0's prepared base.
     */
    public void setBaseLocation(String key, @NotNull Location location) {
        if (configuration == null) return;
        configuration.set("base." + key, Utils.getLocationConfigString(location));
        try {
            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, areaName, "配置", "保存",
                    "无法保存基地坐标 | " + exception.getMessage()));
        }
    }

    public boolean hasBaseLocation(@NotNull String key) {
        return configuration != null && !configuration.getString("base." + key, "").isBlank();
    }

    public void invalidateMovedBaseGeometry() {
        if (configuration != null) configuration.set("base", null);
        areaPos1 = null;
        areaPos2 = null;
    }

    /** Physical grid index for a playable team seat; index 0 is reserved for the editable template. */
    public static int playableCopyIndex(int seat) {
        return seat + 1;
    }

    /** Material cuboids refer to their full WorldEdit block snapshots stored beside this map's schematics. */
    public @NotNull List<BuildMartMaterialZone> getMaterialZones() {
        if (configuration == null) return List.of();
        List<BuildMartMaterialZone> zones = new ArrayList<>();
        for (Map<?, ?> row : configuration.getMapList("material-zones")) {
            Vector pos1 = vector(row.get("pos1"));
            Vector pos2 = vector(row.get("pos2"));
            UUID snapshotId = uuid(row.get("snapshot-id"));
            if (pos1 != null && pos2 != null && snapshotId != null)
                zones.add(new BuildMartMaterialZone(snapshotId, pos1, pos2));
        }
        return List.copyOf(sortMaterialZones(zones));
    }

    /** Map-specific centres inferred from the physical resource-island layout. */
    public @NotNull Map<BuildMartMaterialIsland, Vector> getMaterialIslandCenters() {
        Map<BuildMartMaterialIsland, Vector> copy = new EnumMap<>(BuildMartMaterialIsland.class);
        materialIslandCenters.forEach((island, center) -> copy.put(island, center.clone()));
        return Map.copyOf(copy);
    }

    /** Assigns a zone by horizontal proximity; height only describes the stored centre, not classification. */
    public @Nullable BuildMartMaterialIsland classifyMaterialZone(@NotNull BuildMartMaterialZone zone) {
        return classifyMaterialZone(zone, getMaterialIslandCenters());
    }

    private static @Nullable BuildMartMaterialIsland classifyMaterialZone(
            @NotNull BuildMartMaterialZone zone,
            @NotNull Map<BuildMartMaterialIsland, Vector> centers) {
        BuildMartMaterialIsland nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        double x = (zone.minX() + zone.maxX()) / 2.0;
        double z = (zone.minZ() + zone.maxZ()) / 2.0;
        for (BuildMartMaterialIsland island : BuildMartMaterialIsland.values()) {
            Vector center = centers.get(island);
            if (center == null) continue;
            double dx = x - center.getX();
            double dz = z - center.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < nearestDistance) {
                nearest = island;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public @NotNull List<BuildMartMaterialZone> getMaterialZones(@NotNull BuildMartMaterialIsland island) {
        return getMaterialZonesByIsland().getOrDefault(island, List.of());
    }

    public @NotNull Map<BuildMartMaterialIsland, List<BuildMartMaterialZone>> getMaterialZonesByIsland() {
        Map<BuildMartMaterialIsland, Vector> centers = getMaterialIslandCenters();
        Map<BuildMartMaterialIsland, List<BuildMartMaterialZone>> grouped =
                new EnumMap<>(BuildMartMaterialIsland.class);
        for (BuildMartMaterialIsland island : BuildMartMaterialIsland.values()) {
            grouped.put(island, new ArrayList<>());
        }
        for (BuildMartMaterialZone zone : getMaterialZones()) {
            BuildMartMaterialIsland island = classifyMaterialZone(zone, centers);
            if (island != null) grouped.get(island).add(zone);
        }
        grouped.replaceAll((island, zones) -> List.copyOf(zones));
        return Map.copyOf(grouped);
    }

    private @NotNull List<BuildMartMaterialZone> sortMaterialZones(
            @NotNull List<BuildMartMaterialZone> source) {
        List<BuildMartMaterialZone> sorted = new ArrayList<>(source);
        Map<BuildMartMaterialIsland, Vector> centers = getMaterialIslandCenters();
        sorted.sort(Comparator
                .comparingInt((BuildMartMaterialZone zone) -> {
                    BuildMartMaterialIsland island = classifyMaterialZone(zone, centers);
                    return island == null ? Integer.MAX_VALUE : island.ordinal();
                })
                .thenComparing(Comparator.comparingInt(BuildMartMaterialZone::minY).reversed())
                .thenComparingInt(BuildMartMaterialZone::minZ)
                .thenComparingInt(BuildMartMaterialZone::minX)
                .thenComparing(zone -> zone.snapshotId().toString()));
        return sorted;
    }

    public boolean addMaterialZone(@NotNull BuildMartMaterialZone zone) {
        List<BuildMartMaterialZone> zones = new ArrayList<>(getMaterialZones());
        zones.add(zone);
        return setMaterialZones(zones);
    }

    public boolean clearMaterialZones() {
        return setMaterialZones(List.of());
    }

    public boolean setMaterialZones(@NotNull List<BuildMartMaterialZone> zones) {
        if (configuration == null) return false;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BuildMartMaterialZone zone : sortMaterialZones(zones)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("snapshot-id", zone.snapshotId().toString());
            row.put("pos1", vectorMap(zone.pos1()));
            row.put("pos2", vectorMap(zone.pos2()));
            rows.add(row);
        }
        Object previous = configuration.get("material-zones");
        configuration.set("material-zones", rows);
        try {
            configuration.save(configurationPath.toFile());
            if (!BuildMartMaterialManifest.write(plugin, this)) {
                configuration.set("material-zones", previous);
                configuration.save(configurationPath.toFile());
                plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, areaName, "材料区", "保存",
                        "材料清单更新失败，已回滚材料区配置"));
                return false;
            }
            return true;
        } catch (Exception exception) {
            configuration.set("material-zones", previous);
            try {
                configuration.save(configurationPath.toFile());
            } catch (Exception rollbackException) {
                plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, areaName, "材料区", "回滚",
                        "无法恢复材料区配置 | " + rollbackException.getMessage()));
            }
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, areaName, "配置", "保存",
                    "无法保存材料区 | " + exception.getMessage()));
            return false;
        }
    }

    /** Generated inspection artifact; it is deliberately not read by gameplay or map loading. */
    public @NotNull File getMaterialManifestFile() {
        return new File(new File(new File(plugin.getDataFolder(), "buildmart"), "material-manifests"),
                areaName + ".yml");
    }

    public @NotNull File getMaterialZoneSnapshotFile(@NotNull BuildMartMaterialZone zone) {
        return new File(materialZoneSnapshotDirectory(), zone.snapshotId() + ".schem");
    }

    public void deleteMaterialZoneSnapshot(@NotNull BuildMartMaterialZone zone) {
        File snapshot = getMaterialZoneSnapshotFile(zone);
        if (snapshot.isFile() && !snapshot.delete()) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, areaName, "材料区", "删除",
                    "无法删除材料区快照=" + snapshot.getName()));
        }
    }

    private @NotNull File materialZoneSnapshotDirectory() {
        return new File(new File(new File(plugin.getDataFolder(), "buildmart"), "schematics"),
                areaName + "/material-zones");
    }

    private static @Nullable Vector vector(Object value) {
        if (value instanceof Vector vector) return vector.clone();
        if (value instanceof ConfigurationSection section) {
            Number x = number(section.get("x"));
            Number y = number(section.get("y"));
            Number z = number(section.get("z"));
            return x == null || y == null || z == null
                    ? null : new Vector(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        if (!(value instanceof Map<?, ?> map)) return null;
        Number x = number(map.get("x"));
        Number y = number(map.get("y"));
        Number z = number(map.get("z"));
        return x == null || y == null || z == null
                ? null : new Vector(x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    private static @Nullable Number number(Object value) {
        return value instanceof Number number ? number : null;
    }

    private static @Nullable UUID uuid(Object value) {
        if (!(value instanceof String text)) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Map<String, Object> vectorMap(Vector vector) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", vector.getX());
        map.put("y", vector.getY());
        map.put("z", vector.getZ());
        return map;
    }

    @Override
    protected void loadCustomFileOptions() {
        List<WindZone> zones = new ArrayList<>();
        for (Map<?, ?> row : configuration.getMapList("wind-zones")) {
            Vector pos1 = vector(row.get("pos1"));
            Vector pos2 = vector(row.get("pos2"));
            if (pos1 != null && pos2 != null) zones.add(new WindZone(pos1, pos2));
        }

        // Maps written before v12 have one pair of fields. Keep them usable until the next save.
        if (zones.isEmpty()) {
            Vector legacyPos1 = configuration.getVector("wind-zone-pos1");
            Vector legacyPos2 = configuration.getVector("wind-zone-pos2");
            if (legacyPos1 != null && legacyPos2 != null) zones.add(new WindZone(legacyPos1, legacyPos2));
        }
        setWindZones(zones);

        Map<BuildMartMaterialIsland, Vector> centers = new EnumMap<>(BuildMartMaterialIsland.class);
        for (Map<?, ?> row : configuration.getMapList("material-islands")) {
            BuildMartMaterialIsland island = BuildMartMaterialIsland.byId(
                    row.get("id") instanceof String id ? id : null);
            Vector center = vector(row.get("center"));
            if (island != null && center != null) centers.put(island, center.clone());
        }
        materialIslandCenters = Map.copyOf(centers);
    }

    @Override
    protected void saveCustomOptions() {
        if (configuration == null) return;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WindZone zone : windZones) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pos1", vectorMap(zone.pos1()));
            row.put("pos2", vectorMap(zone.pos2()));
            rows.add(row);
        }
        configuration.set("wind-zones", rows);
        configuration.set("wind-zone-pos1", null);
        configuration.set("wind-zone-pos2", null);

        configuration.set("material-islands", materialIslandRows(materialIslandCenters));
    }

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        migratedConfiguration.set("base.golden-ref", null);
        if (oldConfiguration.getString("world-name", "").isBlank())
            migratedConfiguration.set("world-name", "buildmart");
        if (oldConfiguration.getInt("dont-edit-this.version", 0) < 10) {
            // v10 changes both the physical copy numbering and the portal contract. An old published map
            // must be stamped and reviewed through the new flow before it can be selected for a game.
            migratedConfiguration.set("copy-layout.hub-size", null);
            migratedConfiguration.set("hub-spawn-point", null);
            migratedConfiguration.set("hub-return-pos1", null);
            migratedConfiguration.set("hub-return-pos2", null);
            migratedConfiguration.set("prepare.published", false);
            migratedConfiguration.set("prepare.dirty", true);
            migratedConfiguration.set("prepare.world-built", false);
        }
        if (oldConfiguration.getInt("dont-edit-this.version", 0) < 12
                && oldConfiguration.getMapList("wind-zones").isEmpty()) {
            Vector pos1 = oldConfiguration.getVector("wind-zone-pos1");
            Vector pos2 = oldConfiguration.getVector("wind-zone-pos2");
            if (pos1 != null && pos2 != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("pos1", vectorMap(pos1));
                row.put("pos2", vectorMap(pos2));
                migratedConfiguration.set("wind-zones", List.of(row));
            } else {
                migratedConfiguration.set("wind-zones", List.of());
            }
        }
        migratedConfiguration.set("wind-zone-pos1", null);
        migratedConfiguration.set("wind-zone-pos2", null);
        if (oldConfiguration.getInt("dont-edit-this.version", 0) < 14) {
            migratedConfiguration.set("copy-layout.source-origin", null);
            migratedConfiguration.set("prepare.published", false);
            migratedConfiguration.set("prepare.dirty", true);
            migratedConfiguration.set("prepare.world-built", false);
        }
        if (oldConfiguration.getInt("dont-edit-this.version", 0) < 16
                && oldConfiguration.getMapList("material-islands").isEmpty()
                && "area".equalsIgnoreCase(oldConfiguration.getString("name", ""))) {
            migratedConfiguration.set("material-islands", materialIslandRows(legacyAreaMaterialIslandCenters()));
        }
        if (oldConfiguration.getInt("dont-edit-this.version", 0) < 17) {
            // A configuration upgrade must never reinterpret an old physical ring as a row. Such maps must
            // be rebuilt and published through the normal prepare flow before their anchors can change.
            migratedConfiguration.set("copy-layout.type", "RING");
            migratedConfiguration.set("copy-layout.generated-origin", null);
            migratedConfiguration.set("copy-layout.step", null);
        }
    }

    private static @NotNull List<Map<String, Object>> materialIslandRows(
            @NotNull Map<BuildMartMaterialIsland, Vector> centers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BuildMartMaterialIsland island : BuildMartMaterialIsland.values()) {
            Vector center = centers.get(island);
            if (center == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", island.id());
            row.put("center", vectorMap(center));
            rows.add(row);
        }
        return rows;
    }

    /** One-time v16 recovery for the server's established Build Mart resource-hub geometry. */
    private static @NotNull Map<BuildMartMaterialIsland, Vector> legacyAreaMaterialIslandCenters() {
        Map<BuildMartMaterialIsland, Vector> centers = new EnumMap<>(BuildMartMaterialIsland.class);
        centers.put(BuildMartMaterialIsland.WHITE, new Vector(190.0, 132.0, 64.0));
        centers.put(BuildMartMaterialIsland.ORANGE, new Vector(240.0, 142.0, 78.0));
        centers.put(BuildMartMaterialIsland.MAGENTA, new Vector(281.0, 148.0, 102.0));
        centers.put(BuildMartMaterialIsland.LIGHT_BLUE, new Vector(310.0, 140.0, 145.0));
        centers.put(BuildMartMaterialIsland.YELLOW, new Vector(316.0, 136.0, 191.0));
        centers.put(BuildMartMaterialIsland.LIME, new Vector(306.0, 131.0, 241.0));
        centers.put(BuildMartMaterialIsland.PINK, new Vector(279.0, 134.0, 276.0));
        centers.put(BuildMartMaterialIsland.GRAY, new Vector(239.0, 138.0, 308.0));
        centers.put(BuildMartMaterialIsland.LIGHT_GRAY, new Vector(191.0, 146.0, 317.0));
        centers.put(BuildMartMaterialIsland.CYAN, new Vector(144.0, 141.0, 308.0));
        centers.put(BuildMartMaterialIsland.PURPLE, new Vector(105.0, 134.0, 281.0));
        centers.put(BuildMartMaterialIsland.BLUE, new Vector(75.0, 140.0, 240.0));
        centers.put(BuildMartMaterialIsland.BROWN, new Vector(65.0, 135.0, 192.0));
        centers.put(BuildMartMaterialIsland.GREEN, new Vector(74.0, 147.0, 146.0));
        centers.put(BuildMartMaterialIsland.RED, new Vector(103.0, 147.0, 103.0));
        centers.put(BuildMartMaterialIsland.BLACK, new Vector(142.0, 140.0, 75.0));
        centers.put(BuildMartMaterialIsland.PLANTS, new Vector(151.0, 166.16, 387.36));
        centers.put(BuildMartMaterialIsland.NETHER, new Vector(2.737, 166.842, 153.079));
        centers.put(BuildMartMaterialIsland.TREES, new Vector(230.0, 167.0, 0.955));
        centers.put(BuildMartMaterialIsland.BRICKS, new Vector(80.0, 167.0, 31.875));
        centers.put(BuildMartMaterialIsland.STONE, new Vector(382.3, 167.05, 229.2));
        centers.put(BuildMartMaterialIsland.MINERALS, new Vector(24.289, 166.474, 298.526));
        centers.put(BuildMartMaterialIsland.SAND_GRAVEL, new Vector(335.75, 167.0, 85.0));
        centers.put(BuildMartMaterialIsland.COPPER, new Vector(303.4, 167.0, 340.5));
        return Map.copyOf(centers);
    }
}
