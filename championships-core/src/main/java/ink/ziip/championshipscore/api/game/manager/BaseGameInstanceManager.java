package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.setup.MapSetupTarget;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public abstract class BaseGameInstanceManager<T extends BaseGameInstance> extends BaseManager {
    protected final ConcurrentHashMap<String, T> areas = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, List<T>> instancesByMap = new ConcurrentHashMap<>();
    private final Set<String> managedWorlds = new LinkedHashSet<>();
    private long mapLoadGeneration;

    public BaseGameInstanceManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    /** Runs first-tick map initialization only while this manager still owns the request. */
    protected final void deferMapLoad(Runnable initialize) {
        long generation = ++mapLoadGeneration;
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (generation == mapLoadGeneration && plugin.isEnabled())
                                initialize.run();
                        });
    }

    /** Common map-file discovery; one invalid definition does not prevent loading other maps. */
    protected final void loadMapDefinitions(File directory, BiConsumer<String, File> register) {
        List<File> files;
        try {
            Files.createDirectories(directory.toPath());
            try (var entries = Files.list(directory.toPath())) {
                files =
                        entries.filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().endsWith(".yml"))
                                .sorted()
                                .map(java.nio.file.Path::toFile)
                                .toList();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read map definitions: " + directory, exception);
        }
        for (File file : files) {
            String filename = file.getName();
            String name = filename.substring(0, filename.length() - 4);
            try {
                register.accept(name, file);
            } catch (RuntimeException exception) {
                plugin.getLogger()
                        .log(Level.SEVERE, "Cannot load map definition: " + file, exception);
            }
        }
    }

    protected final void stopOwnedAreas() {
        for (T area : getRuntimeInstances()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) area.abortAndReset();
        }
    }

    @Override
    public void unload() {
        stopOwnedAreas();
        clearAreas();
    }

    public List<String> getAreaNameList() {
        return new java.util.ArrayList<>(areas.keySet());
    }

    /**
     * All permanent runtime instances owned by this manager. Replicated-map managers include every
     * slot.
     */
    public synchronized Collection<T> getRuntimeInstances() {
        Set<T> instances = new LinkedHashSet<>();
        instancesByMap.values().forEach(instances::addAll);
        areas.forEach(
                (name, area) -> {
                    if (!instancesByMap.containsKey(name)) instances.add(area);
                });
        return List.copyOf(instances);
    }

    public synchronized @NotNull List<T> getMapInstances(@NotNull String name) {
        List<T> instances = instancesByMap.get(name);
        if (instances != null) return List.copyOf(instances);
        T area = areas.get(name);
        return area == null ? List.of() : List.of(area);
    }

    /** Publishes a representative and its owned runtime slots under one manager lock. */
    protected final synchronized void registerMapInstances(String name, List<T> instances) {
        if (instances.isEmpty())
            throw new IllegalArgumentException("A map needs a runtime instance");
        instancesByMap.put(name, new ArrayList<>(instances));
        areas.put(name, instances.getFirst());
    }

    /**
     * Resolves the configured admin teleport anchor for a physical world. Replicated maps are
     * sorted by copy index so copy 0 is selected consistently; map name is a deterministic
     * tie-breaker for shared-world game types.
     */
    @Nullable
    public Location getWorldTeleportLocation(@NotNull String worldName) {
        return getRuntimeInstances().stream()
                .filter(instance -> worldName.equals(instance.getWorldName()))
                .sorted(
                        Comparator.comparingInt(BaseGameInstance::getCopyIndex)
                                .thenComparing(
                                        instance ->
                                                Objects.toString(
                                                        instance.getGameConfig().getConfigName(),
                                                        ""),
                                        String.CASE_INSENSITIVE_ORDER))
                .map(
                        instance -> {
                            try {
                                Location configured =
                                        GameSpawnResolver.resolve(instance.getGameConfig());
                                if (configured != null && configured.getWorld() == null) {
                                    World world = Bukkit.getWorld(instance.getWorldName());
                                    if (world != null) configured.setWorld(world);
                                }
                                return configured != null
                                        ? configured
                                        : instance.getAdminTeleportLocation();
                            } catch (RuntimeException ignored) {
                                return null;
                            }
                        })
                .filter(
                        location ->
                                location != null
                                        && location.getWorld() != null
                                        && worldName.equals(location.getWorld().getName()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public T getArea(String name) {
        return areas.get(name);
    }

    public abstract boolean addArea(String name);

    /**
     * Registers a map against an already loaded physical world. Map editing must never create
     * worlds; callers are expected to create/load the world through the admin world command first.
     */
    public boolean addArea(String name, String worldName) {
        return addArea(name);
    }

    /**
     * Removes only the map definition/runtime objects. The physical world is deliberately retained.
     */
    public synchronized boolean deleteArea(String name) {
        T representative = areas.get(name);
        if (representative == null || !canEditMap(name)) return false;
        try {
            java.nio.file.Files.deleteIfExists(
                    plugin.getFolder().resolve(representative.getGameConfig().getFileName()));
            detachAreaRegistration(name, representative);
            return true;
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("无法删除地图配置 " + name + " | " + exception.getMessage());
            return false;
        }
    }

    /** Map configuration used by administrative lifecycle operations such as an atomic rename. */
    @Nullable
    public BaseGameConfig getMapConfig(@NotNull String name) {
        T representative = areas.get(name);
        return representative == null ? null : representative.getGameConfig();
    }

    /**
     * Only the selected map's runtime copies must be idle; another region in a shared world may
     * run.
     */
    public synchronized boolean canRenameArea(@NotNull String name) {
        T representative = areas.get(name);
        if (representative == null) return false;
        BaseGameConfig config = representative.getGameConfig();
        return getRuntimeInstances().stream()
                .filter(instance -> instance.getGameConfig() == config)
                .allMatch(
                        instance ->
                                instance.getGameStageEnum()
                                        == ink.ziip.championshipscore.api.game.model.GameStageEnum
                                                .WAITING);
    }

    /** Unregisters and disposes one map without deleting its configuration or physical world. */
    public synchronized boolean detachAreaForRename(@NotNull String name) {
        T representative = areas.get(name);
        if (representative == null || !canRenameArea(name)) return false;
        detachAreaRegistration(name, representative);
        return true;
    }

    /** Rollback-only variant which also removes a partially loading replacement instance. */
    public synchronized boolean forceDetachAreaAfterFailedRename(@NotNull String name) {
        T representative = areas.get(name);
        if (representative == null) return true;
        detachAreaRegistration(name, representative);
        return true;
    }

    private void detachAreaRegistration(@NotNull String name, @NotNull T representative) {
        BaseGameConfig config = representative.getGameConfig();
        List<T> copies = new ArrayList<>();
        for (T instance : getRuntimeInstances()) {
            if (instance.getGameConfig() == config) copies.add(instance);
        }
        copies.forEach(BaseGameInstance::dispose);
        onAreaDetached(name);
        areas.remove(name, representative);
    }

    /** Releases the selected map's runtime-slot index during deletion or rename. */
    protected void onAreaDetached(@NotNull String name) {
        instancesByMap.remove(name);
    }

    /** Loads an existing renamed configuration. Managers with setup-only add paths may override. */
    public synchronized boolean loadAreaAfterRename(
            @NotNull String name, @NotNull String worldName) {
        return addArea(name, worldName);
    }

    /**
     * Returns the map-definition surface used by prepare. Runtime instances remain an
     * implementation detail of the game manager and are not retained by the prepare session.
     */
    @Nullable
    public SetupTarget getSetupTarget(GameTypeEnum gameType, String name) {
        T representative = areas.get(name);
        if (representative == null) return null;
        return new MapSetupTarget(plugin, gameType, name, representative.getGameConfig(), this);
    }

    public boolean canEditMap(String name) {
        T representative = areas.get(name);
        if (representative == null) return false;
        BaseGameConfig config = representative.getGameConfig();
        return getRuntimeInstances().stream()
                .filter(instance -> instance.getGameConfig() == config)
                .allMatch(
                        instance ->
                                instance.getGameStageEnum()
                                        == ink.ziip.championshipscore.api.game.model.GameStageEnum
                                                .WAITING);
    }

    public boolean bindMapWorld(String name, World world) {
        T representative = areas.get(name);
        if (representative == null || !canEditMap(name)) return false;
        boolean usedByOtherMap =
                areas.entrySet().stream()
                        .anyMatch(
                                entry ->
                                        !entry.getKey().equals(name)
                                                && world.getName()
                                                        .equals(entry.getValue().getWorldName()));
        if (usedByOtherMap && !allowsSharedMapWorlds()) return false;
        representative.getGameConfig().bindConfiguredWorld(world.getName());
        representative.getGameConfig().saveOptions();
        return world.getName().equals(representative.getWorldName());
    }

    /** Shared-world games can keep several independent map regions in one physical world. */
    protected boolean allowsSharedMapWorlds() {
        return false;
    }

    /** Public capability query used by admin tooling and architecture tests. */
    public final boolean supportsSharedMapWorlds() {
        return allowsSharedMapWorlds();
    }

    /** Loads and takes ownership of a void arena world for this enabled game. */
    protected boolean loadArenaWorld(String worldName) {
        if (!plugin.getWorldManager().loadWorld(worldName, World.Environment.NORMAL, false))
            return false;
        managedWorlds.add(worldName);
        return true;
    }

    /** Loads and takes ownership of one vanilla-survival Bingo dimension. */
    protected boolean loadBingoWorld(String worldName, World.Environment environment) {
        if (!plugin.getWorldManager().loadBingoWorld(worldName, environment)) return false;
        managedWorlds.add(worldName);
        return true;
    }

    /** Updates ownership after an idle map world has been renamed by the admin world command. */
    public void renameManagedWorld(String oldWorldName, String newWorldName) {
        if (managedWorlds.remove(oldWorldName)) managedWorlds.add(newWorldName);
    }

    public void clearAreas() {
        mapLoadGeneration++;
        getRuntimeInstances().forEach(BaseGameInstance::dispose);
        List.copyOf(areas.keySet()).forEach(this::onAreaDetached);
        instancesByMap.clear();
        areas.clear();
        for (String worldName : managedWorlds)
            plugin.getWorldManager().unloadWorld(worldName, true);
        managedWorlds.clear();
    }
}
