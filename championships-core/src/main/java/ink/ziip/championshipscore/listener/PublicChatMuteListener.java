package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.chat.MuteDuration;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/** Public chat only: the /teammsg command path remains available to muted players. */
public final class PublicChatMuteListener extends BaseListener {
    PublicChatMuteListener(ChampionshipsCore plugin) { super(plugin); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPublicChat(AsyncChatEvent event) {
        var mute = plugin.getPublicChatMuteManager().activeMute(event.getPlayer().getUniqueId());
        if (mute == null) return;
        event.setCancelled(true);
        String notice = (mute.permanent() ? MessageConfig.CHAT_MUTED_PERMANENT : MessageConfig.CHAT_MUTED_TEMPORARY)
                .replace("%reason%", mute.reason())
                .replace("%remaining%", MuteDuration.formatMillis(Math.max(0, mute.expiresAt() - System.currentTimeMillis())));
        plugin.getServer().getScheduler().runTask(plugin, () -> event.getPlayer().sendMessage(Utils.toComponent(notice)));
    }
}
