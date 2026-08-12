package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.setup.MapSetupTarget;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseGameInstanceManager<T extends BaseGameInstance> extends BaseManager {
    protected final ConcurrentHashMap<String, T> areas = new ConcurrentHashMap<>();
    private final Set<String> managedWorlds = new LinkedHashSet<>();

    public BaseGameInstanceManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    public List<String> getAreaNameList() {
        return new java.util.ArrayList<>(areas.keySet());
    }

    /** All permanent runtime instances owned by this manager. Replicated-map managers include every slot. */
    public Collection<T> getRuntimeInstances() {
        return List.copyOf(areas.values());
    }

    /**
     * Resolves the configured admin teleport anchor for a physical world. Replicated maps are sorted
     * by copy index so copy 0 is selected consistently; map name is a deterministic tie-breaker for
     * shared-world game types.
     */
    @Nullable
    public Location getWorldTeleportLocation(@NotNull String worldName) {
        return getRuntimeInstances().stream()
                .filter(instance -> worldName.equals(instance.getWorldName()))
                .sorted(Comparator
                        .comparingInt(BaseGameInstance::getCopyIndex)
                        .thenComparing(instance -> Objects.toString(instance.getGameConfig().getConfigName(), ""),
                                String.CASE_INSENSITIVE_ORDER))
                .map(instance -> {
                    try {
                        Location configured = GameSpawnResolver.resolve(instance.getGameConfig());
                        if (configured != null && configured.getWorld() == null) {
                            World world = Bukkit.getWorld(instance.getWorldName());
                            if (world != null) configured.setWorld(world);
                        }
                        return configured != null ? configured : instance.getAdminTeleportLocation();
                    } catch (RuntimeException ignored) {
                        return null;
                    }
                })
                .filter(location -> location != null && location.getWorld() != null
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
     * Registers a map against an already loaded physical world. Map editing must never create worlds;
     * callers are expected to create/load the world through the admin world command first.
     */
    public boolean addArea(String name, String worldName) {
        // Fixed-world games do not create a world in addArea; retain their established registration path.
        return addArea(name);
    }

    /** Removes only the map definition/runtime objects. The physical world is deliberately retained. */
    public boolean deleteArea(String name) {
        T representative = areas.get(name);
        if (representative == null || !canEditMap(name)) return false;
        try {
            java.nio.file.Files.deleteIfExists(plugin.getFolder().resolve(representative.getGameConfig().getFileName()));
            representative.dispose();
            areas.remove(name, representative);
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

    /** Only the selected map's runtime copies must be idle; another region in a shared world may run. */
    public synchronized boolean canRenameArea(@NotNull String name) {
        T representative = areas.get(name);
        if (representative == null) return false;
        BaseGameConfig config = representative.getGameConfig();
        return getRuntimeInstances().stream()
                .filter(instance -> instance.getGameConfig() == config)
                .allMatch(instance -> instance.getGameStageEnum()
                        == ink.ziip.championshipscore.api.object.stage.GameStageEnum.WAITING);
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

    /** Replicated managers remove their secondary map-to-copy registration here. */
    protected void onAreaDetached(@NotNull String name) {
    }

    /** Loads an existing renamed configuration. Managers with setup-only add paths may override. */
    public synchronized boolean loadAreaAfterRename(@NotNull String name, @NotNull String worldName) {
        return addArea(name, worldName);
    }

    /**
     * Returns the map-definition surface used by prepare. Runtime instances remain an implementation
     * detail of the game manager and are not retained by the prepare session.
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
        String worldName = representative.getWorldName();
        return getRuntimeInstances().stream()
                .filter(instance -> worldName.equals(instance.getWorldName()))
                .allMatch(instance -> instance.getGameStageEnum()
                        == ink.ziip.championshipscore.api.object.stage.GameStageEnum.WAITING);
    }

    public boolean bindMapWorld(String name, World world) {
        T representative = areas.get(name);
        if (representative == null || !canEditMap(name)) return false;
        boolean usedByOtherMap = areas.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(name)
                        && world.getName().equals(entry.getValue().getWorldName()));
        if (usedByOtherMap && !allowsSharedMapWorlds()) return false;
        representative.getGameConfig().bindConfiguredWorld(world.getName());
        representative.getGameConfig().saveOptions();
        return world.getName().equals(representative.getWorldName());
    }

    /** Shared-world games can keep several independent map regions in one physical world. */
    protected boolean allowsSharedMapWorlds() {
        return false;
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
        if (!plugin.getWorldManager().loadBingoWorld(worldName, environment))
            return false;
        managedWorlds.add(worldName);
        return true;
    }

    /** Updates ownership after an idle map world has been renamed by the admin world command. */
    public void renameManagedWorld(String oldWorldName, String newWorldName) {
        if (managedWorlds.remove(oldWorldName))
            managedWorlds.add(newWorldName);
    }

    public void clearAreas() {
        getRuntimeInstances().forEach(BaseGameInstance::dispose);
        areas.clear();
        for (String worldName : managedWorlds)
            plugin.getWorldManager().unloadWorld(worldName, true);
        managedWorlds.clear();
    }
}
