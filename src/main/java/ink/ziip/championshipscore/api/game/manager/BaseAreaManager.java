package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseAreaManager<T extends BaseArea> extends BaseManager {
    protected final ConcurrentHashMap<String, T> areas = new ConcurrentHashMap<>();

    public BaseAreaManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    public List<String> getAreaNameList() {
        return new java.util.ArrayList<>(areas.keySet());
    }

    @Nullable
    public T getArea(String name) {
        return areas.get(name);
    }

    public abstract boolean addArea(String name);

    public void clearAreas() {
        areas.clear();
    }
}
