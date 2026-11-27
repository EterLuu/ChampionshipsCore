package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderArt;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderMapIds;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** One ID pool per plugin. Overlapping arenas lease the same edition of the eight postcards. */
public final class SliderMaps {
    private final Plugin plugin;
    private final TileRenderer[] renderers = new TileRenderer[SliderMapIds.COUNT];
    private final MapView[] views = new MapView[SliderMapIds.COUNT];
    private Method renderCanvas;
    private boolean inspectedCanvas;
    private final ItemStack[] items = new ItemStack[SliderMapIds.COUNT];
    final SliderWork work;
    final JointWork jointWork;
    private final Path path;
    private final Set<Integer> used = new HashSet<>();
    private List<Object> saved;
    private int bound;
    private boolean dirty;
    private Edition current;
    private SliderMapPackets retiring;

    public SliderMaps(Plugin plugin) {
        this.plugin = plugin;
        work = new SliderWork(plugin);
        jointWork = new JointWork(plugin);
        path = plugin.getDataFolder().toPath().resolve("iamnotarobot/slider-maps.yml");
    }

    static final class Edition {
        final long seed = ThreadLocalRandom.current().nextLong();
        CompletableFuture<byte[][]> artwork;
        int retryAt;
        final Set<SliderAudience> halls = new LinkedHashSet<>();
        final Map<World, Integer> worlds = new HashMap<>();
        final SliderBandwidth bandwidth = new SliderBandwidth();
        final SliderDelivery delivery = new SliderDelivery();
        org.bukkit.scheduler.BukkitTask stream;
        final SliderMapPackets packets = new SliderMapPackets();
        boolean published;
        int primed;
        byte[][] screens;
    }

    Edition acquire(World world) {
        if (current == null) {
            closeRetiredPackets();
            Edition edition = new Edition();
            current = edition;
            readIds();
            plugin.getLogger().info("Preparing slider postcards " + edition.seed);
            generate(edition);
            edition.stream =
                    Bukkit.getScheduler().runTaskTimer(plugin, () -> prefetch(edition), 1, 1);
        }
        current.worlds.merge(world, 1, Integer::sum);
        return current;
    }

    private void prefetch(Edition edition) {
        if (current != edition) return;
        long now = System.nanoTime();
        edition.bandwidth.observe(now, edition.packets.drainBytes());
        if (bound < items.length && saved != null) {
            long deadline = now + 2_000_000L;
            var world = edition.worlds.keySet().iterator().next();
            for (int i = 0; i < 64 && System.nanoTime() < deadline; i++)
                if (prepareSlice(world)) break;
        }
        publishIfReady(edition);
        if (!edition.published || bound == 0) return;
        for (var world : edition.worlds.keySet()) {
            for (var viewer : world.getPlayers()) {
                edition.delivery.enter(viewer.getUniqueId());
                if (!edition.delivery.needsPixels(viewer.getUniqueId())) continue;
                var location = viewer.getLocation();
                for (var hall : edition.halls) hall.prioritize(viewer, location);
            }
        }
        edition.delivery.slice(
                now,
                edition.bandwidth,
                (id, slot) -> {
                    var p = Bukkit.getPlayer(id);
                    return p != null
                            && edition.worlds.containsKey(p.getWorld())
                            && push(edition, p, slot);
                });
        if (bound == items.length && edition.primed < views.length) {
            long deadline = System.nanoTime() + 1_500_000L;
            for (int i = 0; i < 48 && System.nanoTime() < deadline; i++) {
                if (primeCanvas(edition)) {
                    edition.primed = views.length;
                    break;
                }
            }
        }
    }

    private void generate(Edition edition) {
        var result = new CompletableFuture<byte[][]>();
        edition.artwork = result;
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                byte[][] screens = new byte[SliderHall.values().length * 2][];
                                for (var hall : SliderHall.values()) {
                                    for (int round = 1; round <= 2; round++) {
                                        if (result.isCancelled()) return;
                                        var card = SliderArt.postcard(edition.seed, hall, round);
                                        screens[2 * hall.ordinal() + round - 1] =
                                                SliderArt.mapPixels(card.draw());
                                    }
                                }
                                result.complete(screens);
                            } catch (RuntimeException failure) {
                                result.completeExceptionally(failure);
                            }
                        });
    }

    /** Only invoked by a generation-checked match clock on the game thread. */
    void publishIfReady(Edition edition) {
        if (edition == null || edition != current || edition.published) return;
        if (edition.artwork == null) {
            if (Bukkit.getCurrentTick() - edition.retryAt >= 0) generate(edition);
            return;
        }
        if (!edition.artwork.isDone()) return;
        byte[][] screens;
        try {
            screens = edition.artwork.join();
        } catch (CompletionException failure) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Retrying slider postcards in five seconds",
                            failure.getCause());
            edition.artwork = null;
            edition.retryAt = Bukkit.getCurrentTick() + 100;
            return;
        }
        edition.screens = screens;
        edition.published = true;
        plugin.getLogger().info("Published slider postcards " + edition.seed);
    }

    void release(Edition edition, World world) {
        if (edition == null || edition != current) return;
        edition.worlds.computeIfPresent(world, (w, n) -> n == 1 ? null : n - 1);
        if (!edition.worlds.containsKey(world) && world != null)
            for (var p : world.getPlayers()) forget(p);
        if (!edition.worlds.isEmpty()) return;
        closeEdition(edition);
        saveIds();
    }

    private void closeEdition(Edition edition) {
        edition.stream.cancel();
        if (!edition.published)
            plugin.getLogger().info("Discarded slider postcards " + edition.seed);
        retiring = edition.packets;
        retiring.retire();
        work.add(
                () -> retiring == edition.packets,
                () -> {
                    if (work.removing()) return false;
                    closeRetiredPackets();
                    return true;
                },
                1,
                false);
        if (edition.artwork != null) edition.artwork.cancel(false);
        current = null;
    }

    private void closeRetiredPackets() {
        if (retiring != null) {
            retiring.close();
            retiring = null;
        }
    }

    boolean ready(Edition edition) {
        return edition == current && edition.published && bound == items.length;
    }

    private void publishTile(Edition edition, int index) {
        var pixels = edition.screens[index / SliderMapIds.TILES];
        if (renderers[index].screen != pixels) {
            renderers[index].set(pixels, index % SliderMapIds.TILES);
            edition.packets.publish(views[index].getId(), renderers[index].tile);
        }
    }

    ItemStack item(Edition edition, SliderHall hall, int round, int tile) {
        int index = SliderMapIds.index(hall, round, tile);
        publishTile(edition, index);
        return items[index];
    }

    @SuppressWarnings("deprecation")
    boolean push(Edition edition, Player viewer, int slot) {
        if (edition != current || !edition.published || slot >= bound) return false;
        publishTile(edition, slot);
        return edition.packets.push(
                viewer,
                views[slot].getId(),
                views[slot].getScale().getValue(),
                renderers[slot].tile);
    }

    void forget(Player viewer) {
        if (current != null) {
            current.delivery.forget(viewer.getUniqueId());
            current.packets.forget(viewer);
        }
    }

    private void readIds() {
        if (bound == items.length) return;
        if (saved == null) {
            var yaml = new YamlConfiguration();
            try {
                if (Files.exists(path)) yaml.load(path.toFile());
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
                throw new IllegalStateException("Cannot read slider map IDs", ex);
            }
            saved = new ArrayList<>(yaml.getList("ids", List.of()));
        }
    }

    // Bukkit has no prepare-canvas API. Paper exposes this public CraftMapView method;
    // the non-contextual renderer has no viewer and emits no packet during preparation.
    // Other implementations still render normally when the first viewer tracks the tile.
    private boolean primeCanvas(Edition edition) {
        if (edition.primed >= views.length) return true;
        if (!inspectedCanvas) {
            inspectedCanvas = true;
            renderCanvas =
                    Arrays.stream(views[0].getClass().getMethods())
                            .filter(
                                    m ->
                                            m.getName().equals("render")
                                                    && m.getParameterCount() == 1
                                                    && Player.class.isAssignableFrom(
                                                            m.getParameterTypes()[0]))
                            .findFirst()
                            .orElse(null);
        }
        if (renderCanvas == null) return true;
        int cursor = edition.primed++;
        int round = cursor / (4 * SliderMapIds.TILES);
        int station = (cursor / SliderMapIds.TILES) % 4;
        int index = (2 * station + round) * SliderMapIds.TILES + cursor % SliderMapIds.TILES;
        publishTile(edition, index);
        try {
            renderCanvas.invoke(views[index], (Object) null);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Slider canvas preparation unavailable; using first-view rendering",
                            ex);
            renderCanvas = null;
            return true;
        }
        return edition.primed == views.length;
    }

    private boolean prepareSlice(World world) {
        if (bound == items.length) return true;
        Object old = bound < saved.size() ? saved.get(bound) : null;
        int id =
                SliderMapIds.resolveSlot(
                        old,
                        used,
                        n -> Bukkit.getMap(n) != null,
                        () -> Bukkit.createMap(world).getId());
        if (!Objects.equals(old, id)) {
            if (bound < saved.size()) saved.set(bound, id);
            else saved.add(id);
            dirty = true;
        }
        var map = Objects.requireNonNull(Bukkit.getMap(id));
        map.setTrackingPosition(false);
        map.setUnlimitedTracking(false);
        map.setLocked(true);
        for (var renderer : map.getRenderers()) map.removeRenderer(renderer);
        renderers[bound] = new TileRenderer();
        map.addRenderer(renderers[bound]);
        views[bound] = map;
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        item.editMeta(MapMeta.class, meta -> meta.setMapView(map));
        items[bound++] = item;
        if (bound != items.length) return false;
        if (saved.size() > bound) {
            saved.subList(bound, saved.size()).clear();
            dirty = true;
        }
        saveIds();
        return true;
    }

    private void saveIds() {
        if (!dirty) return;
        var yaml = new YamlConfiguration();
        yaml.set("ids", saved);
        try {
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), "slider-maps-", ".tmp");
            try {
                yaml.save(temp.toFile());
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                dirty = false;
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist slider map IDs", e);
        }
    }

    public void unload() {
        if (current != null) closeEdition(current);
        work.unload();
        jointWork.unload();
        closeRetiredPackets();
        saveIds();
        for (int i = 0; i < bound; i++) {
            views[i].removeRenderer(renderers[i]);
            views[i] = null;
            items[i] = null;
            renderers[i] = null;
        }
        renderCanvas = null;
        inspectedCanvas = false;
        saved = null;
        bound = 0;
        used.clear();
    }

    static final class TileRenderer extends MapRenderer {
        private byte[] screen;
        private byte[] tile;
        private int version, drawn = -1;

        TileRenderer() {
            super(false);
        }

        void set(byte[] pixels, int tile) {
            screen = pixels;
            int offset =
                    tile / SliderHall.MAP_COLUMNS * 128 * SliderArt.WIDTH
                            + tile % SliderHall.MAP_COLUMNS * 128;
            this.tile = new byte[128 * 128];
            if (pixels == null) Arrays.fill(this.tile, (byte) 34);
            else
                for (int y = 0; y < 128; y++)
                    System.arraycopy(pixels, offset + y * SliderArt.WIDTH, this.tile, y * 128, 128);
            version++;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void render(MapView view, MapCanvas canvas, Player player) {
            if (drawn == version) return;
            for (int y = 0; y < 128; y++) {
                int start = y * 128;
                for (int x = 0; x < 128; x++)
                    canvas.setPixel(x, y, tile == null ? (byte) 34 : tile[start + x]);
            }
            drawn = version;
        }
    }
}
