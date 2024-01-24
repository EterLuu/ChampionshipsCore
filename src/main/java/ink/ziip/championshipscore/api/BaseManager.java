package ink.ziip.championshipscore.api;

import ink.ziip.championshipscore.ChampionshipsCore;

public abstract class BaseManager {
    protected final ChampionshipsCore plugin;

    public BaseManager(ChampionshipsCore championshipsCore) {
        this.plugin = championshipsCore;
    }

    public abstract void load();

    public abstract void unload();
}
