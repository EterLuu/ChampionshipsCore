package ink.ziip.championshipscore.api.game.buildmart.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.SourceAnchoredRowArenaGrid;
import ink.ziip.championshipscore.api.game.buildmart.geometry.BuildMartMapGeometry;
import ink.ziip.championshipscore.api.game.buildmart.geometry.BuildMartRowLayoutPlanner;
import ink.ziip.championshipscore.api.game.buildmart.mechanics.BuildMartJumpPads;
import ink.ziip.championshipscore.api.game.buildmart.model.BuildMartMaterialZone;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartBase;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartMaterialIsland;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.location.LocationConfig;
import ink.ziip.championshipscore.logging.LogText;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
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
 * Per-map Build Mart configuration. Maps may share a physical world; geometry is split between the
 * hand-built central resource market and a single base template. Copy 0 is reserved for that
 * template; the playable team bases begin at copy 1 and runtime reset restores only this map's
 * configured regions.
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

    @Override
    public Vector getAreaPos1() {
        return null;
    }

    @Override
    public Vector getAreaPos2() {
        return null;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Bound by the prepare flow before publication; blank in a new draft template. */
    @ConfigOption(path = "world-name", nullable = true)
    private String worldName;

    /** Round duration in seconds. Default 15 minutes. */
    @ConfigOption(path = "timer")
    private int timer = 900;

    /** Number of playable team bases physically stamped into this map (copies 1..N). */
    @ConfigOption(path = "base-count")
    private int baseCount = 8;

    /** Build Mart team bases are placed in the configured row layout. */
    @ConfigOption(path = "copy-layout.type")
    private String copyLayoutType = "ROW";

    @ConfigOption(path = "copy-layout.source-origin", nullable = true)
    private Vector baseSourceOrigin;

    @ConfigOption(path = "copy-layout.generated-origin", nullable = true)
    private Vector copyLayoutGeneratedOrigin;

    @ConfigOption(path = "copy-layout.step", nullable = true)
    private Vector copyLayoutStep;

    @ConfigOption(path = "copy-layout.base-size", nullable = true)
    private Vector baseSchematicSize;

    public @NotNull ArenaGrid getBaseGrid() {
        if (!isRowLayout()) throw new IllegalStateException("copy-layout.type must be ROW");
        Vector source = baseSourceOrigin == null ? new Vector(0, 100, 0) : baseSourceOrigin.clone();
        Vector generated =
                copyLayoutGeneratedOrigin == null ? source : copyLayoutGeneratedOrigin.clone();
        Vector step = copyLayoutStep == null ? new Vector(1, 0, 0) : copyLayoutStep.clone();
        return new SourceAnchoredRowArenaGrid(source, generated, step);
    }

    /**
     * Keeps copy 0 at the source schematic's original minimum corner and generates playable copies
     * east of all configured infrastructure in the row layout.
     */
    public @NotNull ArenaGrid prepareBaseGrid(
            @NotNull Vector baseOrigin, @NotNull Vector baseSize) {
        baseSourceOrigin = baseOrigin.clone();
        baseSchematicSize = baseSize.clone();
        copyLayoutGeneratedOrigin =
                BuildMartRowLayoutPlanner.generatedOrigin(
                        baseOrigin, configuredInfrastructureMaxX(baseOrigin, baseSize));
        copyLayoutStep = BuildMartRowLayoutPlanner.step(baseSize);
        return getBaseGrid();
    }

    public boolean isRowLayout() {
        return "ROW".equalsIgnoreCase(copyLayoutType);
    }

    /** New maps opt into row placement before their first schematic is captured. */
    public void useRowLayoutForDraft() {
        copyLayoutType = "ROW";
    }

    /**
     * Highest known infrastructure coordinate, used to keep a freshly generated row clear of the
     * hub.
     */
    public double configuredInfrastructureMaxX(
            @NotNull Vector baseOrigin, @NotNull Vector baseSize) {
        double max = baseOrigin.getX() + baseSize.getBlockX();
        max = maxX(max, hubPos1, hubPos2);
        max =
                maxX(
                        max,
                        spectatorSpawnPoint,
                        hubPortalPoint,
                        goldenDisplayPoint,
                        getIntroductionSpawnPoint());
        for (JumpPadZone zone : jumpPads) max = maxX(max, zone.pos1(), zone.pos2());
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
        for (Location location : locations)
            if (location != null) max = Math.max(max, location.getX());
        return max;
    }

    /**
     * Records copy 0's real selection corner and invalidates copies made from an older template.
     */
    public void recordBaseTemplateOrigin(@NotNull Vector baseOrigin) {
        baseSourceOrigin = baseOrigin.clone();
        prepareWorldBuilt = false;
        saveOptions();
    }

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

    /** WorldEdit selections of the surfaces that act as jump pads. */
    private List<JumpPadZone> jumpPads = List.of();

    /**
     * Persisted centres of the 24 physical material islands, keyed by their stable semantic
     * identity.
     */
    private Map<BuildMartMaterialIsland, Vector> materialIslandCenters = Map.of();

    public record JumpPadZone(@NotNull Vector pos1, @NotNull Vector pos2) {
        public JumpPadZone {
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
    public @NotNull List<JumpPadZone> getJumpPads() {
        return List.copyOf(jumpPads);
    }

    public void setJumpPads(@Nullable List<JumpPadZone> zones) {
        if (zones == null || zones.isEmpty()) {
            jumpPads = List.of();
            return;
        }
        jumpPads = List.copyOf(zones);
    }

    /** Anchor where the current golden blueprint is pasted in the hub for players to observe. */
    @ConfigOption(path = "golden-display-point", nullable = true)
    private Location goldenDisplayPoint;

    /** How often (seconds) the golden blueprint is swapped; it stays live for this whole window. */
    @ConfigOption(path = "golden-refresh-seconds")
    private int goldenRefreshSeconds = 180;

    /** Cooldown (ms) on portal triggers to stop the player bouncing back and forth. */
    @ConfigOption(path = "portal-cooldown-millis")
    private long portalCooldownMillis = 1000L;

    /**
     * The single configured 0th-base template, or {@code null} if unconfigured. Read live from the
     * {@code base} section so the per-leaf writes from {@link #setBaseLocation} are never clobbered
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
     * Geometry for the playable base assigned to {@code seat} (0-based). The editable template is
     * physical copy 0, so seat 0 resolves to physical copy 1 and is never assigned the template
     * itself.
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
     * True when a location is inside the shared resource hub or one of the stamped team-base
     * cuboids. Build Mart has no single enclosing arena box: the bases are deliberately separated
     * around the hub.
     */
    public boolean isInPlayableArea(@Nullable Location location) {
        if (location == null
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getConfiguredWorld())) {
            return false;
        }
        if (isInJumpPadFlightPath(location)) return true;
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
            if (x >= origin.getBlockX()
                    && x < origin.getBlockX() + width
                    && y >= origin.getBlockY()
                    && y < origin.getBlockY() + height
                    && z >= origin.getBlockZ()
                    && z < origin.getBlockZ() + depth) {
                return true;
            }
        }
        return false;
    }

    /** Keeps the mostly vertical trajectory and its small forward displacement inside the arena. */
    private boolean isInJumpPadFlightPath(@NotNull Location location) {
        if (jumpPads.isEmpty()
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getConfiguredWorld())) return false;
        for (JumpPadZone zone : jumpPads) {
            Vector min = Vector.getMinimum(zone.pos1(), zone.pos2());
            Vector max = Vector.getMaximum(zone.pos1(), zone.pos2());
            if (location.getX() >= min.getX() - BuildMartJumpPads.FLIGHT_MARGIN
                    && location.getX() <= max.getX() + 1.0 + BuildMartJumpPads.FLIGHT_MARGIN
                    && location.getZ() >= min.getZ() - BuildMartJumpPads.FLIGHT_MARGIN
                    && location.getZ() <= max.getZ() + 1.0 + BuildMartJumpPads.FLIGHT_MARGIN
                    && location.getY() >= min.getY()
                    && location.getY() <= BuildMartJumpPads.TARGET_Y + 1.0) return true;
        }
        return false;
    }

    /** True when a location is inside the stamped base cuboid for a particular seat. */
    public boolean isInBase(@NotNull Location location, int seat) {
        if (location.getWorld() == null
                || !location.getWorld().getName().equals(getConfiguredWorld())) return false;
        Vector size = baseSchematicSize;
        if (size == null || seat < 0 || seat >= Math.max(0, baseCount)) return false;
        Vector origin = getBaseGrid().origin(playableCopyIndex(seat));
        return insideCuboid(location, origin, size);
    }

    /** True when a location is inside the physical 0th template cuboid. */
    public boolean isInBaseTemplate(@NotNull Location location) {
        if (location.getWorld() == null
                || !location.getWorld().getName().equals(getConfiguredWorld())) return false;
        if (baseSchematicSize == null) return false;
        return insideCuboid(location, getBaseGrid().origin(0), baseSchematicSize);
    }

    private static boolean insideCuboid(
            @NotNull Location location, @NotNull Vector origin, @NotNull Vector size) {
        return location.getX() >= origin.getX()
                && location.getX() < origin.getX() + Math.max(1, size.getX())
                && location.getY() >= origin.getY()
                && location.getY() < origin.getY() + Math.max(1, size.getY())
                && location.getZ() >= origin.getZ()
                && location.getZ() < origin.getZ() + Math.max(1, size.getZ());
    }

    /**
     * Writes a string-serialized location into the {@code base.<key>} template section and persists
     * it. Used by the area set-up commands; the admin configures these standing in seat 0's
     * prepared base.
     */
    public void setBaseLocation(String key, @NotNull Location location) {
        if (configuration == null) return;
        configuration.set("base." + key, LocationConfig.asString(location));
        try {
            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    GameTypeEnum.BuildMart,
                                    areaName,
                                    "配置",
                                    "保存",
                                    "无法保存基地坐标 | " + exception.getMessage()));
        }
    }

    public boolean hasBaseLocation(@NotNull String key) {
        return configuration != null && !configuration.getString("base." + key, "").isBlank();
    }

    public void invalidateMovedBaseGeometry() {
        if (configuration != null) configuration.set("base", null);
    }

    /**
     * Physical grid index for a playable team seat; index 0 is reserved for the editable template.
     */
    public static int playableCopyIndex(int seat) {
        return seat + 1;
    }

    /**
     * Material cuboids refer to their full WorldEdit block snapshots stored beside this map's
     * schematics.
     */
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

    /**
     * Assigns a zone by horizontal proximity; height only describes the stored centre, not
     * classification.
     */
    public @Nullable BuildMartMaterialIsland classifyMaterialZone(
            @NotNull BuildMartMaterialZone zone) {
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

    public @NotNull List<BuildMartMaterialZone> getMaterialZones(
            @NotNull BuildMartMaterialIsland island) {
        return getMaterialZonesByIsland().getOrDefault(island, List.of());
    }

    public @NotNull Map<BuildMartMaterialIsland, List<BuildMartMaterialZone>>
            getMaterialZonesByIsland() {
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
        sorted.sort(
                Comparator.comparingInt(
                                (BuildMartMaterialZone zone) -> {
                                    BuildMartMaterialIsland island =
                                            classifyMaterialZone(zone, centers);
                                    return island == null ? Integer.MAX_VALUE : island.ordinal();
                                })
                        .thenComparing(
                                Comparator.comparingInt(BuildMartMaterialZone::minY).reversed())
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
                plugin.getLogger()
                        .warning(
                                LogText.formatGameLog(
                                        GameTypeEnum.BuildMart,
                                        areaName,
                                        "材料区",
                                        "保存",
                                        "材料清单更新失败，已回滚材料区配置"));
                return false;
            }
            return true;
        } catch (Exception exception) {
            configuration.set("material-zones", previous);
            try {
                configuration.save(configurationPath.toFile());
            } catch (Exception rollbackException) {
                plugin.getLogger()
                        .warning(
                                LogText.formatGameLog(
                                        GameTypeEnum.BuildMart,
                                        areaName,
                                        "材料区",
                                        "回滚",
                                        "无法恢复材料区配置 | " + rollbackException.getMessage()));
            }
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    GameTypeEnum.BuildMart,
                                    areaName,
                                    "配置",
                                    "保存",
                                    "无法保存材料区 | " + exception.getMessage()));
            return false;
        }
    }

    /** Generated inspection artifact; it is deliberately not read by gameplay or map loading. */
    public @NotNull File getMaterialManifestFile() {
        return new File(
                new File(new File(plugin.getDataFolder(), "buildmart"), "material-manifests"),
                areaName + ".yml");
    }

    public @NotNull File getMaterialZoneSnapshotFile(@NotNull BuildMartMaterialZone zone) {
        return new File(materialZoneSnapshotDirectory(), zone.snapshotId() + ".schem");
    }

    public void deleteMaterialZoneSnapshot(@NotNull BuildMartMaterialZone zone) {
        File snapshot = getMaterialZoneSnapshotFile(zone);
        if (snapshot.isFile() && !snapshot.delete()) {
            plugin.getLogger()
                    .warning(
                            LogText.formatGameLog(
                                    GameTypeEnum.BuildMart,
                                    areaName,
                                    "材料区",
                                    "删除",
                                    "无法删除材料区快照=" + snapshot.getName()));
        }
    }

    private @NotNull File materialZoneSnapshotDirectory() {
        return new File(
                new File(new File(plugin.getDataFolder(), "buildmart"), "schematics"),
                areaName + "/material-zones");
    }

    private static @Nullable Vector vector(Object value) {
        if (value instanceof Vector vector) return vector.clone();
        if (value instanceof ConfigurationSection section) {
            Number x = number(section.get("x"));
            Number y = number(section.get("y"));
            Number z = number(section.get("z"));
            return x == null || y == null || z == null
                    ? null
                    : new Vector(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        if (!(value instanceof Map<?, ?> map)) return null;
        Number x = number(map.get("x"));
        Number y = number(map.get("y"));
        Number z = number(map.get("z"));
        return x == null || y == null || z == null
                ? null
                : new Vector(x.doubleValue(), y.doubleValue(), z.doubleValue());
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
        List<JumpPadZone> zones = new ArrayList<>();
        for (Map<?, ?> row : configuration.getMapList("jump-pads")) {
            Vector pos1 = vector(row.get("pos1"));
            Vector pos2 = vector(row.get("pos2"));
            if (pos1 != null && pos2 != null) zones.add(new JumpPadZone(pos1, pos2));
        }

        setJumpPads(zones);

        Map<BuildMartMaterialIsland, Vector> centers = new EnumMap<>(BuildMartMaterialIsland.class);
        for (Map<?, ?> row : configuration.getMapList("material-islands")) {
            BuildMartMaterialIsland island =
                    BuildMartMaterialIsland.byId(row.get("id") instanceof String id ? id : null);
            Vector center = vector(row.get("center"));
            if (island != null && center != null) centers.put(island, center.clone());
        }
        materialIslandCenters = Map.copyOf(centers);
    }

    @Override
    protected void saveCustomOptions() {
        if (configuration == null) return;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JumpPadZone zone : jumpPads) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pos1", vectorMap(zone.pos1()));
            row.put("pos2", vectorMap(zone.pos2()));
            rows.add(row);
        }
        configuration.set("jump-pads", rows);

        configuration.set("material-islands", materialIslandRows(materialIslandCenters));
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
}
