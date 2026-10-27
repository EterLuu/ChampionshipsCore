package ink.ziip.championshipscore.api.game.laserbox.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.location.ConfiguredLocation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Owns the fixed LaserBox rule-lesson copy of the four Laser Showdown sub-arenas.
 *
 * <p>The blocks are copied into the LaserBox world at the fixed origin below. The Laser Showdown
 * YAML is used only as the source of the authored spawn coordinates; no player is sent to, or made
 * dependent on, the Laser Showdown world at runtime.
 */
public final class LaserBoxIntroductionArena implements AutoCloseable {
    private static final int REQUIRED_LANES = 4;
    private static final String LOCAL_WORLD_NAME = "laserbox";
    private static final int SOURCE_MIN_X = -214;
    private static final int SOURCE_MIN_Y = -37;
    private static final int SOURCE_MIN_Z = -268;

    /** Minimum block of the copied lesson area in the LaserBox world. */
    private static final int LOCAL_MIN_X = 4096;

    private static final int LOCAL_MIN_Y = -37;
    private static final int LOCAL_MIN_Z = -268;
    private static final int SOURCE_TO_LOCAL_X = LOCAL_MIN_X - SOURCE_MIN_X;
    private static final int SOURCE_TO_LOCAL_Y = LOCAL_MIN_Y - SOURCE_MIN_Y;
    private static final int SOURCE_TO_LOCAL_Z = LOCAL_MIN_Z - SOURCE_MIN_Z;
    private static final double SPAWN_OCCUPANCY_RADIUS_SQUARED = 16.0D;
    private static final double LANE_HORIZONTAL_MARGIN = 8.0D;
    private static final double LANE_VERTICAL_MARGIN_BELOW = 8.0D;
    private static final double LANE_VERTICAL_MARGIN_ABOVE = 20.0D;

    private record Lane(List<Location> spawns, BoundingBox bounds) {}

    private final ChampionshipsCore plugin;
    private List<Lane> lanes = List.of();
    private Location spectatorSpawn;
    private LaserBoxArea owner;
    private boolean closed;
    private final int[] spawnCursors = new int[REQUIRED_LANES];

    public LaserBoxIntroductionArena(ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    /** Loads the first Laser Showdown map that has the four authored sub-arena spawn groups. */
    public void load() {
        World localWorld = ensureLocalWorld();
        if (localWorld == null) {
            lanes = List.of();
            spectatorSpawn = null;
            plugin.getLogger()
                    .warning("LaserBox rule-introduction disabled: LaserBox world is unavailable");
            return;
        }

        // Read the authored Laser Showdown coordinates as data only. This keeps the introduction
        // available even when Laser Showdown is disabled, without loading or registering its world.
        File directory = new File(plugin.getDataFolder(), "lasershowdown");
        if (directory.isDirectory()) {
            File[] files =
                    directory.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
            if (files != null) {
                java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
                for (File file : files) {
                    String mapName = file.getName().substring(0, file.getName().length() - 4);
                    if (useConfig(mapName, YamlConfiguration.loadConfiguration(file), localWorld))
                        return;
                }
            }
        }
        lanes = List.of();
        spectatorSpawn = null;
        plugin.getLogger()
                .warning(
                        "LaserBox rule-introduction disabled: Laser Showdown has fewer than four"
                                + " configured sub-arenas");
    }

    private boolean useConfig(String mapName, YamlConfiguration config, World localWorld) {
        List<Lane> candidate = readLanes(config, localWorld);
        if (candidate.size() < REQUIRED_LANES) return false;
        ConfiguredLocation configuredSpectator =
                ConfiguredLocation.read(config.get("spectator-spawn-point"));
        Location spectator = toLocal(configuredSpectator, localWorld);
        if (spectator == null) return false;
        lanes = List.copyOf(candidate.subList(0, REQUIRED_LANES));
        spectatorSpawn = spectator.clone();
        plugin.getLogger()
                .info(
                        "LaserBox rule-introduction uses the local LaserBox copy of Laser Showdown"
                                + " map "
                                + mapName
                                + " sub-arenas "
                                + lanes.size()
                                + " at "
                                + LOCAL_WORLD_NAME
                                + ":"
                                + LOCAL_MIN_X
                                + ":"
                                + LOCAL_MIN_Y
                                + ":"
                                + LOCAL_MIN_Z);
        return true;
    }

    private World ensureLocalWorld() {
        World existing = Bukkit.getWorld(LOCAL_WORLD_NAME);
        if (existing != null) return existing;
        try {
            File worldFolder = plugin.getWorldManager().getWorldFolder(LOCAL_WORLD_NAME);
            if (worldFolder.isDirectory())
                plugin.getWorldManager()
                        .loadWorld(LOCAL_WORLD_NAME, World.Environment.NORMAL, false);
        } catch (RuntimeException ignored) {
            // The normal LaserBox map loader will report a missing world separately.
        }
        return Bukkit.getWorld(LOCAL_WORLD_NAME);
    }

    private static List<Lane> readLanes(YamlConfiguration config, World localWorld) {
        ConfigurationSection groups = config.getConfigurationSection("player-spawn-points");
        if (groups == null) return List.of();
        List<Lane> result = new ArrayList<>();
        for (String key :
                groups.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            List<Location> spawns = new ArrayList<>();
            for (String serialized : groups.getStringList(key)) {
                Location location = toLocal(ConfiguredLocation.read(serialized), localWorld);
                if (location != null) spawns.add(location);
            }
            if (spawns.isEmpty()) continue;
            result.add(new Lane(List.copyOf(spawns), bounds(spawns)));
        }
        return result;
    }

    private static Location toLocal(ConfiguredLocation configured, World localWorld) {
        if (configured == null || localWorld == null) return null;
        return new Location(
                localWorld,
                configured.x() + SOURCE_TO_LOCAL_X,
                configured.y() + SOURCE_TO_LOCAL_Y,
                configured.z() + SOURCE_TO_LOCAL_Z,
                configured.yaw(),
                configured.pitch());
    }

    private static BoundingBox bounds(List<Location> spawns) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Location spawn : spawns) {
            minX = Math.min(minX, spawn.getX());
            minY = Math.min(minY, spawn.getY());
            minZ = Math.min(minZ, spawn.getZ());
            maxX = Math.max(maxX, spawn.getX());
            maxY = Math.max(maxY, spawn.getY());
            maxZ = Math.max(maxZ, spawn.getZ());
        }
        return new BoundingBox(
                minX - LANE_HORIZONTAL_MARGIN,
                minY - LANE_VERTICAL_MARGIN_BELOW,
                minZ - LANE_HORIZONTAL_MARGIN,
                maxX + LANE_HORIZONTAL_MARGIN,
                maxY + LANE_VERTICAL_MARGIN_ABOVE,
                maxZ + LANE_HORIZONTAL_MARGIN);
    }

    public boolean availableTo(LaserBoxArea area) {
        return !closed
                && spectatorSpawn != null
                && lanes.size() == REQUIRED_LANES
                && (owner == null || owner == area);
    }

    public LaserBoxIntroductionRun begin(LaserBoxArea area) {
        if (!availableTo(area) || owner != null) return null;
        owner = area;
        for (int i = 0; i < spawnCursors.length; i++) spawnCursors[i] = 0;
        return new LaserBoxIntroductionRun(plugin, area, this);
    }

    boolean ownedBy(LaserBoxArea area) {
        return !closed && owner == area;
    }

    void release(LaserBoxArea area) {
        if (owner == area) owner = null;
    }

    Location spectatorSpawn() {
        return spectatorSpawn == null ? null : spectatorSpawn.clone();
    }

    boolean contains(int lane, Location location) {
        return lane >= 0
                && lane < lanes.size()
                && location != null
                && location.getWorld() != null
                && lanes.get(lane).spawns().getFirst().getWorld().equals(location.getWorld())
                && lanes.get(lane).bounds().contains(location.toVector());
    }

    Location leastPopulatedSpawn(
            int lane,
            LaserBoxArea area,
            Set<java.util.UUID> assignedPlayers,
            java.util.UUID excluded) {
        if (lane < 0 || lane >= lanes.size()) return spectatorSpawn();
        List<Location> spawns = lanes.get(lane).spawns();
        List<Player> players = new ArrayList<>();
        for (java.util.UUID uuid : assignedPlayers) {
            if (uuid.equals(excluded)) continue;
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && area.isIntroductionParticipant(player)) players.add(player);
        }
        int minimum = Integer.MAX_VALUE;
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < spawns.size(); index++) {
            Location spawn = spawns.get(index);
            int nearby = 0;
            for (Player player : players)
                if (player.getLocation().distanceSquared(spawn) <= SPAWN_OCCUPANCY_RADIUS_SQUARED)
                    nearby++;
            if (nearby < minimum) {
                minimum = nearby;
                candidates.clear();
            }
            if (nearby == minimum) candidates.add(index);
        }
        if (candidates.isEmpty()) return spawns.getFirst().clone();
        int selected = candidates.get(spawnCursors[lane]++ % candidates.size());
        return spawns.get(selected).clone();
    }

    int laneCount() {
        return lanes.size();
    }

    List<Location> preloadLocations() {
        return lanes.stream().flatMap(lane -> lane.spawns().stream()).map(Location::clone).toList();
    }

    @Override
    public void close() {
        if (owner != null) owner.cancelIntroduction();
        owner = null;
        closed = true;
        lanes = List.of();
        spectatorSpawn = null;
    }
}
