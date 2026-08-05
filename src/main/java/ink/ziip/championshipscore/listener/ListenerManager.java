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
        playerListener = new PlayerListener(plugin);
        protectionListener = new ProtectionListener(plugin);
        playerListener.register();
        protectionListener.register();
    }

    @Override
    public void unload() {
        if (playerListener != null) {
            playerListener.unRegister();
            playerListener = null;
        }
        if (protectionListener != null) {
            protectionListener.unRegister();
            protectionListener = null;
        }
    }
}
