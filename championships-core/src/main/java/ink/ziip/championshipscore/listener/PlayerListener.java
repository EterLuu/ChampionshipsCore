package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.platform.bukkit.text.ChampionshipTabText;
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
        PlayerPresentation presentation = presentation(player);
        final Component messageOverride;
        if (player.hasPermission("cc.refuge")) {
            // Referees may colour their own message; identity still follows the same contract as TAB.
            String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
            messageOverride = Utils.toComponent("&f" + typed);
        } else {
            messageOverride = null;
        }

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component actualMessage = messageOverride != null ? messageOverride : message;
            return ChampionshipTabText.chatLine(presentation.label(), presentation.teamColorCode(),
                    presentation.activePlayer(), player.getName(), actualMessage);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent event) {
        PlayerIdentityMigrationResult migration = plugin.getPlayerManager()
                .prepareIdentity(event.getName(), event.getUniqueId());
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(event.getUniqueId());
        String name = event.getName();
        boolean hasResolvedTeam = migration.successful() && !migration.hasTeamConflict()
                && migration.resolvedTeamId() != null;

        if (Bukkit.getOnlinePlayers().size() >= CCConfig.MAX_PLAYERS) {
            if (CCConfig.WHITELIST.contains(name))
                return;

            if (championshipTeam == null && !hasResolvedTeam) {
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
        playerManager.updatePlayer(player);
        PlayerPresentation presentation = presentation(player);
        event.joinMessage(Component.translatable("multiplayer.player.joined",
                ChampionshipTabText.playerIdentityComponent(presentation.label(), presentation.teamColorCode(),
                        presentation.activePlayer(), player.getName())));

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
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerPresentation presentation = presentation(player);
        event.quitMessage(Component.translatable("multiplayer.player.left",
                ChampionshipTabText.playerIdentityComponent(presentation.label(), presentation.teamColorCode(),
                        presentation.activePlayer(), player.getName())));
    }

    private PlayerPresentation presentation(Player player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        BaseGameInstance playerArea = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        boolean daily = plugin.getDailyManager() != null && plugin.getDailyManager().isDailyLobby();
        if (daily) {
            BaseGameInstance shownArea = playerArea;
            if (shownArea == null)
                shownArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            String label = shownArea == null ? "&a大厅" : "&6" + shownArea.getGameTypeEnum();
            return new PlayerPresentation(label, team == null ? null : team.getColorCode(),
                    playerArea != null && team != null);
        }
        String label = team == null ? MessageConfig.PLACEHOLDER_SPECTATOR : team.getColoredName();
        return new PlayerPresentation(label, team == null ? null : team.getColorCode(),
                playerArea != null && team != null);
    }

    private record PlayerPresentation(String label, String teamColorCode, boolean activePlayer) {
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

}
