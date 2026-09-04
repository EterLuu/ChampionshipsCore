package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.platform.bukkit.text.CrossServerChatText;
import ink.ziip.championshipscore.platform.bukkit.text.PlayerPresentation;
import ink.ziip.championshipscore.platform.bukkit.text.TeamChatCommandParser;
import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import ink.ziip.championshipscore.util.Utils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class PlayerListener extends BaseListener {
    private final AtomicInteger onlinePlayerCount;
    private final Set<NamespacedKey> recipeKeys;

    protected PlayerListener(ChampionshipsCore plugin) {
        super(plugin);
        onlinePlayerCount = new AtomicInteger(plugin.getServer().getOnlinePlayers().size());
        Set<NamespacedKey> discovered = new HashSet<>();
        plugin.getServer().recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof Keyed keyedRecipe) discovered.add(keyedRecipe.getKey());
        });
        recipeKeys = Set.copyOf(discovered);
        plugin.getRedisManager().setChatReceiver(this::receiveCrossServerChat);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerPresentation presentation = presentation(player);
        Component messageOverride = refereeMessage(player, event.message());

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component actualMessage = messageOverride != null ? messageOverride : message;
            return presentation.chatLine(player.getName(), actualMessage);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void publishCrossServerChat(AsyncChatEvent event) {
        if (!plugin.getRedisManager().isReady()) return;
        String sourceInstance = plugin.getRedisManager().instanceId();
        if (sourceInstance == null || sourceInstance.isBlank()) return;
        Player player = event.getPlayer();
        PlayerPresentation presentation = presentation(player);
        Component override = refereeMessage(player, event.message());
        Component message = override == null ? event.message() : override;
        plugin.getRedisManager().publishChat(CrossServerChatText.message(sourceInstance,
                player.getUniqueId(), player.getName(), presentation, message, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeamMessageCommand(PlayerCommandPreprocessEvent event) {
        String message = TeamChatCommandParser.messageBody(event.getMessage());
        if (message == null) return;
        event.setCancelled(true);
        Player sender = event.getPlayer();
        if (message.isEmpty()) {
            sender.sendMessage(Utils.toComponent(MessageConfig.CHAT_TEAM_USAGE));
            return;
        }
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(sender);
        if (team == null) {
            sender.sendMessage(Utils.toComponent(MessageConfig.CHAT_TEAM_UNAVAILABLE));
            return;
        }
        PlayerPresentation presentation = presentation(sender);
        Component line = Utils.toComponent(MessageConfig.CHAT_TEAM_PREFIX)
                .append(presentation.chatLine(sender.getName(), Component.text(message)));
        team.getOnlinePlayers().forEach(player -> player.sendMessage(line));
    }

    private Component refereeMessage(Player player, Component message) {
        if (!player.hasPermission(ChampionshipPermissions.REFEREE)) return null;
        // Referees may colour their own message; identity still follows the same contract as TAB.
        String typed = PlainTextComponentSerializer.plainText().serialize(message);
        return Utils.toComponent("&f" + typed);
    }

    private void receiveCrossServerChat(CrossServerChatMessage message) {
        final Component line;
        try {
            line = CrossServerChatText.render(message);
        } catch (RuntimeException malformed) {
            plugin.getLogger().log(Level.WARNING,
                    "Rejected malformed cross-server chat component " + message.messageId(), malformed);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getServer().getOnlinePlayers().forEach(player -> player.sendMessage(line));
            plugin.getServer().getConsoleSender().sendMessage(line);
        });
    }

    void detachChatReceiver() {
        plugin.getRedisManager().setChatReceiver(null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent event) {
        // AuthBridge runs at LOWEST. Never create or migrate Core records for a
        // player that access control has already rejected.
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        PlayerIdentityMigrationResult migration = plugin.getPlayerManager()
                .prepareIdentity(event.getName(), event.getUniqueId());
        if (!migration.successful()) {
            event.kickMessage(LegacyComponentSerializer.legacySection()
                    .deserialize(Utils.translateColorCodes(MessageConfig.IDENTITY_VERIFICATION_FAILED)));
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            return;
        }
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(event.getUniqueId());
        String name = event.getName();
        boolean hasResolvedTeam = migration.successful() && !migration.hasTeamConflict()
                && migration.resolvedTeamId() != null;

        if (onlinePlayerCount.get() >= CCConfig.MAX_PLAYERS) {
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
        onlinePlayerCount.incrementAndGet();
        Player player = event.getPlayer();
        PlayerManager playerManager = ChampionshipsCore.getInstance().getPlayerManager();
        playerManager.updatePlayer(player);
        PlayerPresentation presentation = presentation(player);
        event.joinMessage(Component.translatable("multiplayer.player.joined",
                presentation.identity(player.getName())));

        // Let normal join/teleport notices finish first, then restore a recent result that may have
        // been missed while this player was disconnected.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline())
                plugin.getRankManager().replayRecentRankingSummary(player);
        }, 40L);

        player.discoverRecipes(recipeKeys);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlinePlayerCount.updateAndGet(current -> Math.max(0, current - 1));
        Player player = event.getPlayer();
        PlayerPresentation presentation = presentation(player);
        event.quitMessage(Component.translatable("multiplayer.player.left",
                presentation.identity(player.getName())));
    }

    private PlayerPresentation presentation(Player player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        BaseGameInstance playerArea = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        boolean daily = plugin.getDailyManager() != null && plugin.getDailyManager().isDailyLobby();
        if (daily) {
            if (plugin.getTeamManager().isTransientTeam(team)) {
                return new PlayerPresentation(team.getColoredName(), team.getColorCode(), playerArea != null);
            }
            BaseGameInstance shownArea = playerArea;
            if (shownArea == null)
                shownArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            String label = shownArea == null ? MessageConfig.PRESENTATION_DAILY_LOBBY
                    : MessageConfig.PRESENTATION_DAILY_GAME.replace("%game%", shownArea.getGameTypeEnum().toString());
            return new PlayerPresentation(label, team == null ? null : team.getColorCode(),
                    playerArea != null && team != null);
        }
        String label = team == null ? MessageConfig.PLACEHOLDER_SPECTATOR : team.getColoredName();
        return new PlayerPresentation(label, team == null ? null : team.getColorCode(),
                playerArea != null && team != null);
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
