package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.setup.MapSetupTarget;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Collection;
import java.util.Set;
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

    @Nullable
    public T getArea(String name) {
        return areas.get(name);
    }

    public abstract boolean addArea(String name);

    /**
     * Returns the map-definition surface used by prepare. Runtime instances remain an implementation
     * detail of the game manager and are not retained by the prepare session.
     */
    @Nullable
    public SetupTarget getSetupTarget(GameTypeEnum gameType, String name) {
        T representative = areas.get(name);
        if (representative == null) return null;
        return new MapSetupTarget(plugin, gameType, name, representative.getGameConfig(),
                representative.getWorldName(), this);
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

    /** Temporary storage bridge while template persistence is moved fully out of GameInstance. */
    public boolean saveSetupMap(String name, World.Environment environment) {
        T representative = areas.get(name);
        return representative != null && representative.saveMap(environment);
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
