package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.ArenaChunkPreloader;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideIntroductionConfig;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.ConfiguredLocation;
import ink.ziip.championshipscore.configuration.location.LocationConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/** Owns one fixed teaching world and its chunk leases, never a parallel game-instance registry. */
public final class RiptideIntroductionArena implements AutoCloseable {
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
            if (geometry.floorY() - 4 < start.getWorld().getMinHeight()
                    || geometry.floorY() + 10 >= start.getWorld().getMaxHeight())
                throw new IllegalArgumentException("Teaching arena exceeds the world height");
            List<Location> chunks = new ArrayList<>();
            for (int x = (start.getBlockX() - 10) >> 4; x <= (start.getBlockX() + 10) >> 4; x++)
                for (int z = (start.getBlockZ() - 10) >> 4;
                        z <= (start.getBlockZ() + 147) >> 4;
                        z++)
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
        if (config.getConfiguration().getBoolean("prototype-built", false)) {
            finishBuild();
            return;
        }
        var blocks = new ArrayList<>(RiptideIntroductionBlueprint.blocks().entrySet());
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
                                        }
                                        if (index[0] == blocks.size()) {
                                            buildTask.cancel();
                                            buildTask = null;
                                            world().save();
                                            config.getConfiguration().set("prototype-built", true);
                                            config.saveRuntimeConfiguration();
                                            finishBuild();
                                        }
                                    } catch (Exception error) {
                                        fail(error);
                                    }
                                },
                                1L,
                                1L);
    }

    private void finishBuild() {
        // The moving deck strip is reserved for mechanics. Recover even after an abrupt shutdown
        // saved a moving raft or a painted floor; docks, water and authored decoration stay intact.
        for (int f = -geometry.halfLength();
                f <= RiptideIntroductionLesson.FINISH_STEP + geometry.halfLength();
                f++)
            for (int x = -geometry.halfWidth(); x <= geometry.halfWidth(); x++)
                world().getBlockAt(geometry.blockX(f, x), geometry.floorY(), geometry.blockZ(f, x))
                        .setType(
                                Math.abs(f) <= geometry.halfLength()
                                        ? org.bukkit.Material.OAK_PLANKS
                                        : org.bukkit.Material.AIR,
                                false);
        for (var entity : world().getEntities())
            if (entity.getScoreboardTags().contains("cc_riptide_introduction")) entity.remove();
        world().setSpawnLocation(geometry.centerAt(-8));
        int[] steps = {-8, 12, 40, 70, 78, 111, 142};
        String[] keys = {"welcome", "follow", "pass", "color", "dodge", "speed", "finish"};
        for (int index = 0; index < steps.length; index++) {
            Location at = geometry.centerAt(steps[index]).add(index == 0 ? 0 : 4.75, 1.45, 0);
            String label = RiptideIntroductionRun.text(keys[index]);
            TextDisplay display =
                    world().spawn(
                                    at,
                                    TextDisplay.class,
                                    text -> {
                                        text.text(LegacyText.component(label));
                                        text.setBillboard(Display.Billboard.CENTER);
                                        text.setLineWidth(250);
                                        text.setPersistent(false);
                                        text.addScoreboardTag("cc_riptide_introduction");
                                    });
            labels.add(display);
        }
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
        owner = area;
        return new RiptideIntroductionRun(plugin, area, this, geometry);
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
        return geometry.centerAt(0).add(0, 3, -8);
    }

    boolean contains(Location location) {
        return location.getWorld() == world()
                && location.toVector()
                        .isInAABB(geometry.areaMinimum(7, 8), geometry.areaMaximum(7, 12));
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
