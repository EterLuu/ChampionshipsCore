package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.player.CCPlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener extends BaseListener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        CCPlayerManager CCPlayerManager = ChampionshipsCore.getInstance().getCcPlayerManager();
        CCPlayerManager.deletePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        CCPlayerManager CCPlayerManager = ChampionshipsCore.getInstance().getCcPlayerManager();
        CCPlayerManager.getPlayer(event.getPlayer()).updatePlayer();
    }
}
