package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.ArenaChunkPreloader;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideIntroductionConfig;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGenerator;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.ConfiguredLocation;
import ink.ziip.championshipscore.configuration.location.LocationConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/** Owns one fixed teaching world and its chunk leases, never a parallel game-instance registry. */
public final class RiptideIntroductionArena implements AutoCloseable {
    /** Marks every non-persistent lesson entity so a restart can sweep leftovers. */
    static final String TAG = "cc_riptide_introduction";

    private final ChampionshipsCore plugin;
    private final RiptideIntroductionConfig config;
    private final Set<ArenaChunkPreloader.ChunkTicket> tickets = new HashSet<>();
    private final List<TextDisplay> labels = new ArrayList<>();
    private RiptideCourseGeometry geometry;
    private RiptideRushArea owner;
    private BukkitTask buildTask;
    private boolean closed;
    private boolean ready;

    public RiptideIntroductionArena(ChampionshipsCore plugin) {
        this.plugin = plugin;
        config = new RiptideIntroductionConfig(plugin);
    }

    public void load() {
        try {
            config.initializeConfiguration(plugin.getFolder());
            if (!config.isEnabled()) return;
            ConfiguredLocation spawn =
                    ConfiguredLocation.read(config.getConfiguration().get("spawn"));
            if (spawn == null || spawn.world() == null || !spawn.world().matches("[a-z0-9_]+"))
                throw new IllegalArgumentException("Teaching spawn requires a plain world name");
            // A lesson must never write into a lobby, saved race map or unrelated existing world.
            if (!spawn.world().equals("riptide_introduction"))
                throw new IllegalArgumentException("Teaching world must be riptide_introduction");
            if (!plugin.getWorldManager().loadWorld(spawn.world(), World.Environment.NORMAL, false))
                throw new IllegalStateException("Cannot load the Riptide teaching world");
            Location start =
                    spawn.resolve(id -> LocationConfig.resolveWorld(plugin.getServer(), id));
            geometry =
                    RiptideCourseGeometry.resolve(
                            start,
                            start.clone().add(0, 0, RiptideIntroductionLesson.COURSE_LENGTH),
                            7,
                            9);
            if (geometry.floorY() + RiptideIntroductionBlueprint.MIN_HEIGHT
                            < start.getWorld().getMinHeight()
                    || geometry.floorY() + RiptideIntroductionBlueprint.MAX_HEIGHT
                            >= start.getWorld().getMaxHeight())
                throw new IllegalArgumentException("Teaching arena exceeds the world height");
            // Lease every chunk of the build footprint, whichever way the route is turned.
            Set<Long> keys = new HashSet<>();
            List<int[]> corners = new ArrayList<>();
            List<Location> chunks = new ArrayList<>();
            for (int f :
                    new int[] {
                        RiptideIntroductionBlueprint.MIN_FORWARD,
                        RiptideIntroductionBlueprint.MAX_FORWARD
                    })
                for (int x :
                        new int[] {
                            -RiptideIntroductionBlueprint.MAX_LATERAL,
                            RiptideIntroductionBlueprint.MAX_LATERAL
                        }) corners.add(new int[] {geometry.blockX(f, x), geometry.blockZ(f, x)});
            int minX = corners.stream().mapToInt(c -> c[0]).min().orElseThrow() >> 4;
            int maxX = corners.stream().mapToInt(c -> c[0]).max().orElseThrow() >> 4;
            int minZ = corners.stream().mapToInt(c -> c[1]).min().orElseThrow() >> 4;
            int maxZ = corners.stream().mapToInt(c -> c[1]).max().orElseThrow() >> 4;
            for (int x = minX; x <= maxX; x++)
                for (int z = minZ; z <= maxZ; z++)
                    if (keys.add(((long) x << 32) ^ (z & 0xffffffffL)))
                        chunks.add(new Location(start.getWorld(), x * 16, start.getY(), z * 16));
            ArenaChunkPreloader.preload(plugin, chunks, 0, tickets, () -> !closed)
                    .whenComplete(
                            (unused, error) -> {
                                if (closed) return;
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (closed || !plugin.isLoaded()) return;
                                                    if (error != null) fail(error);
                                                    else build();
                                                });
                            });
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    private void build() {
        if (config.getConfiguration().getInt("prototype-version", 0)
                >= RiptideIntroductionBlueprint.VERSION) {
            finishBuild();
            return;
        }
        // An older prototype is replaced as a whole: clear the footprint, then place the new one.
        var blueprint = RiptideIntroductionBlueprint.blocks();
        var blocks = new ArrayList<Map.Entry<RiptideIntroductionBlueprint.Cell, Material>>();
        for (int f = RiptideIntroductionBlueprint.MIN_FORWARD;
                f <= RiptideIntroductionBlueprint.MAX_FORWARD;
                f++)
            for (int x = -RiptideIntroductionBlueprint.MAX_LATERAL;
                    x <= RiptideIntroductionBlueprint.MAX_LATERAL;
                    x++)
                for (int y = RiptideIntroductionBlueprint.MIN_HEIGHT;
                        y <= RiptideIntroductionBlueprint.MAX_HEIGHT;
                        y++) {
                    var cell = new RiptideIntroductionBlueprint.Cell(f, x, y);
                    if (!blueprint.containsKey(cell)) blocks.add(Map.entry(cell, Material.AIR));
                }
        blocks.addAll(blueprint.entrySet());
        final int[] index = {0};
        buildTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (closed || !plugin.isLoaded()) {
                                        close();
                                        return;
                                    }
                                    try {
                                        for (int n = 0;
                                                n < 1000 && index[0] < blocks.size();
                                                n++, index[0]++) {
                                            var entry = blocks.get(index[0]);
                                            var cell = entry.getKey();
                                            world().getBlockAt(
                                                            geometry.blockX(
                                                                    cell.forward(), cell.lateral()),
                                                            geometry.floorY() + cell.height(),
                                                            geometry.blockZ(
                                                                    cell.forward(), cell.lateral()))
                                                    .setType(entry.getValue(), false);
                                            if (entry.getValue() == Material.JUNGLE_LEAVES) {
                                                var block =
                                                        world().getBlockAt(
                                                                        geometry.blockX(
                                                                                cell.forward(),
                                                                                cell.lateral()),
                                                                        geometry.floorY()
                                                                                + cell.height(),
                                                                        geometry.blockZ(
                                                                                cell.forward(),
                                                                                cell.lateral()));
                                                var leaves =
                                                        (org.bukkit.block.data.type.Leaves)
                                                                block.getBlockData();
                                                leaves.setPersistent(true);
                                                block.setBlockData(leaves, false);
                                            }
                                        }
                                        if (index[0] == blocks.size()) {
                                            buildTask.cancel();
                                            buildTask = null;
                                            finishBuild();
                                            world().save();
                                            config.getConfiguration()
                                                    .set(
                                                            "prototype-version",
                                                            RiptideIntroductionBlueprint.VERSION);
                                            config.saveRuntimeConfiguration();
                                        }
                                    } catch (Exception error) {
                                        fail(error);
                                    }
                                },
                                1L,
                                1L);
    }

    private void finishBuild() {
        prepareLighting(world(), null);
        // The moving deck strip is reserved for mechanics. Recover even after an abrupt shutdown
        // saved a moving raft or a painted floor; docks, water and authored decoration stay intact.
        for (int f = -geometry.halfLength();
                f <= RiptideIntroductionLesson.FINISH_STEP + geometry.halfLength();
                f++)
            for (int x = -geometry.halfWidth(); x <= geometry.halfWidth(); x++)
                world().getBlockAt(geometry.blockX(f, x), geometry.floorY(), geometry.blockZ(f, x))
                        .setType(
                                Math.abs(f) <= geometry.halfLength()
                                        ? Material.OAK_PLANKS
                                        : Material.AIR,
                                false);
        for (var entity : world().getEntities())
            if (entity.getScoreboardTags().contains(TAG)) entity.remove();
        world().setSpawnLocation(geometry.centerAt(-10));
        // Labels sit beside the course and never cover a real wall opening.
        for (var sign : RiptideIntroductionBlueprint.SIGNS) {
            Location at = geometry.centerAt(sign.forward()).add(sign.lateral(), 3.5, 0);
            String label = RiptideIntroductionRun.text("zone." + sign.zone());
            TextDisplay display =
                    world().spawn(
                                    at,
                                    TextDisplay.class,
                                    text -> {
                                        text.text(LegacyText.component(label));
                                        MessageConfig.worldTextStyle(
                                                        ink.ziip.championshipscore.presentation
                                                                .display.WorldTextStyles.Profile
                                                                .RIPTIDE_INSTRUCTION)
                                                .apply(text);
                                        text.setPersistent(false);
                                        text.addScoreboardTag(TAG);
                                    });
            labels.add(display);
        }
        // Mechanic volumes are rebuilt too: a crash may have saved a facade or closed rhythm gate.
        for (var level : RiptideIntroductionLesson.plan().levels())
            for (int f = level.step() - level.extent(); f <= level.step() + level.extent(); f++)
                for (int x = -7; x <= 7; x++)
                    for (int y = 1; y <= 12; y++)
                        world().getBlockAt(
                                        geometry.blockX(f, x),
                                        geometry.floorY() + y,
                                        geometry.blockZ(f, x))
                                .setType(Material.AIR, false);
        RiptideCourseGenerator.build(
                world(),
                geometry,
                RiptideIntroductionLesson.plan(),
                Material.OAK_PLANKS,
                Material.STRIPPED_SPRUCE_LOG);
        ready = true;
    }

    private void fail(Throwable error) {
        plugin.getLogger()
                .log(
                        Level.SEVERE,
                        MessageConfig.codeText("riptide-introduction-arena.load-failed"),
                        error);
        close();
    }

    public boolean availableTo(RiptideRushArea area) {
        return ready && !closed && (owner == null || owner == area);
    }

    RiptideIntroductionRun begin(RiptideRushArea area) {
        if (!availableTo(area) || owner != null) return null;
        Location start = area.getGameConfig().getStartPoint();
        prepareLighting(world(), start == null ? null : start.getWorld());
        owner = area;
        return new RiptideIntroductionRun(plugin, area, this, geometry);
    }

    /** Use the selected race's daylight; an idle teaching world defaults to the race's noon. */
    static void prepareLighting(World teaching, World race) {
        teaching.setTime(race == null ? 6000L : race.getTime());
        teaching.setGameRule(GameRules.ADVANCE_TIME, false);
        teaching.setStorm(false);
        teaching.setThundering(false);
        teaching.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    boolean ownedBy(RiptideRushArea area) {
        return !closed && owner == area;
    }

    void release(RiptideRushArea area) {
        if (owner == area) owner = null;
    }

    public Location spawn() {
        return geometry == null ? null : geometry.centerAt(0);
    }

    /** Spawn used by the Riptide map editor to enter the shared teaching world. */
    public Location editorSpawn() {
        Location location = ready && !closed && owner == null ? spawn() : null;
        return location == null ? null : location.clone();
    }

    /** Persists decorative edits made through the map editor without touching the moving deck. */
    public void saveWorld() {
        if (ready && !closed && owner == null && geometry != null) world().save();
    }

    Location spectatorSpawn() {
        // Above the water just behind the first raft, looking down the route.
        return geometry.centerAt(-6).add(0, 4, 0);
    }

    boolean contains(Location location) {
        return location.getWorld() == world()
                && location.toVector()
                        .isInAABB(geometry.areaMinimum(10, 8), geometry.areaMaximum(10, 12));
    }

    private World world() {
        return geometry.centerAt(0).getWorld();
    }

    @Override
    public void close() {
        if (owner != null) owner.cancelIntroduction();
        owner = null;
        closed = true;
        ready = false;
        if (buildTask != null) buildTask.cancel();
        buildTask = null;
        for (TextDisplay label : labels) if (label.isValid()) label.remove();
        labels.clear();
        ArenaChunkPreloader.release(plugin, tickets);
    }
}
