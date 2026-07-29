package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PlayerListener extends BaseListener {
    protected PlayerListener(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        // Pick the chat layout for this sender; both %s slots are name then message (String.format order).
        final String format;
        final Component messageOverride;
        if (player.hasPermission("cc.refuge")) {
            format = Utils.translateColorCodes(CCConfig.CHAT_REFUGEE);
            // Refugees may colour their own message; the leading &f resets any inherited colour.
            String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
            messageOverride = Utils.toComponent("&f" + typed);
        } else if (championshipTeam == null) {
            format = Utils.translateColorCodes(CCConfig.CHAT_SPECTATOR);
            messageOverride = null;
        } else {
            format = Utils.translateColorCodes(CCConfig.CHAT_PLAYER.replace("%team%", championshipTeam.getColoredName()));
            messageOverride = null;
        }

        // Serialise name/message back to legacy, format, and parse the whole line once, so colour codes
        // bleed into the substituted name/message across the %s boundaries.
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component actualMessage = messageOverride != null ? messageOverride : message;
            String nameLegacy = LegacyComponentSerializer.legacySection().serialize(sourceDisplayName);
            String messageLegacy = LegacyComponentSerializer.legacySection().serialize(actualMessage);
            return LegacyComponentSerializer.legacySection().deserialize(String.format(format, nameLegacy, messageLegacy));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = ChampionshipsCore.getInstance().getPlayerManager();
        playerManager.getPlayer(player).updatePlayer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent event) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(event.getUniqueId());
        String name = event.getName();

        if (Bukkit.getOnlinePlayers().size() >= CCConfig.MAX_PLAYERS) {
            if (CCConfig.WHITELIST.contains(name))
                return;

            if (championshipTeam == null) {
                event.kickMessage(LegacyComponentSerializer.legacySection()
                        .deserialize(Utils.translateColorCodes(MessageConfig.SERVER_FULL)));
                event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_FULL);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerManager playerManager = ChampionshipsCore.getInstance().getPlayerManager();
        playerManager.getPlayer(player).updatePlayer();
        playerManager.updatePlayer(player);

        // Let normal join/teleport notices finish first, then restore a recent result that may have
        // been missed while this player was disconnected.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline())
                plugin.getRankManager().replayRecentRankingSummary(player);
        }, 40L);

        plugin.getServer().recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof Keyed keyedRecipe) {
                player.discoverRecipe(keyedRecipe.getKey());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player assailant && event.getEntity() instanceof Player player) {
            ChampionshipTeam assailantChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
            if (assailantChampionshipTeam != null) {
                if (assailantChampionshipTeam.equals(plugin.getTeamManager().getTeamByPlayer(player))) {
                    event.setCancelled(true);
                }
            }
        }

        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Arrow) {
                Projectile projectile = (Projectile) event.getDamager();
                ProjectileSource projectileSource = projectile.getShooter();
                if (!(projectileSource instanceof Player assailant))
                    return;

                ChampionshipTeam assailantChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
                if (assailantChampionshipTeam != null) {
                    if (assailantChampionshipTeam.equals(plugin.getTeamManager().getTeamByPlayer(player))) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHealedBySplashPotion(EntityRegainHealthEvent event) {
        // TODO
    }
}
