package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
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

    /** All permanent runtime instances owned by this manager; one per map in the legacy default. */
    public Collection<T> getRuntimeInstances() {
        return List.copyOf(areas.values());
    }

    @Nullable
    public T getArea(String name) {
        return areas.get(name);
    }

    public abstract boolean addArea(String name);

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

    public void clearAreas() {
        getRuntimeInstances().forEach(BaseGameInstance::dispose);
        areas.clear();
        for (String worldName : managedWorlds)
            plugin.getWorldManager().unloadWorld(worldName, true);
        managedWorlds.clear();
    }
}
