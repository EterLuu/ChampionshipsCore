package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;

public class ListenerManager extends BaseManager {
    private PlayerListener playerListener;
    private ProtectionListener protectionListener;
    private PortalGuardListener portalGuardListener;

    public ListenerManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        playerListener = new PlayerListener(plugin);
        protectionListener = new ProtectionListener(plugin);
        portalGuardListener = new PortalGuardListener(plugin);
        playerListener.register();
        protectionListener.register();
        portalGuardListener.register();
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
        if (portalGuardListener != null) {
            portalGuardListener.unRegister();
            portalGuardListener = null;
        }
    }
}
