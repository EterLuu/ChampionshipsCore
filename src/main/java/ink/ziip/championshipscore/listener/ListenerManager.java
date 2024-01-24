package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;

public class ListenerManager extends BaseManager {
    private static PlayerListener playerListener;
    private static ProtectionListener protectionListener;

    public ListenerManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        playerListener = new PlayerListener();
        protectionListener = new ProtectionListener();
        playerListener.register();
        protectionListener.register();
    }

    @Override
    public void unload() {
        playerListener.unRegister();
        protectionListener.unRegister();
    }
}
