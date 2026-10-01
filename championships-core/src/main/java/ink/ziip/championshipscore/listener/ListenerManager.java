package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;

public class ListenerManager extends BaseManager {
    private PlayerListener playerListener;
    private ProtectionListener protectionListener;
    private PortalGuardListener portalGuardListener;
    private PublicChatMuteListener publicChatMuteListener;

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
        publicChatMuteListener = new PublicChatMuteListener(plugin);
        publicChatMuteListener.register();
    }

    @Override
    public void unload() {
        if (publicChatMuteListener != null) {
            publicChatMuteListener.unRegister();
            publicChatMuteListener = null;
        }
        if (playerListener != null) {
            playerListener.detachChatReceiver();
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
