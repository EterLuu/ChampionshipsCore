package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.player.entry.PlayerIdentityMigrationResult;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.test.TestSessionManager;
import ink.ziip.championshipscore.api.test.auth.TestAuthGateway;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.ChatMessageText;
import ink.ziip.championshipscore.platform.bukkit.text.CrossServerChatText;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.platform.bukkit.text.PlayerPresentation;
import ink.ziip.championshipscore.platform.bukkit.text.TeamChatCommandParser;
import ink.ziip.championshipscore.protocol.CrossServerChatMessage;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class PlayerListener extends BaseListener {
    private static final int TEST_AUTH_CONFIRM_RETRY_LIMIT = 600;
    private static final long TEST_AUTH_CONFIRM_RETRY_TICKS = 5L;
    private static final long TEST_AUTH_CONFIRM_TIMEOUT_NANOS = 120_000_000_000L;
    private final AtomicInteger onlinePlayerCount;
    private final Set<NamespacedKey> recipeKeys;

    /** UUIDs that passed the test lease boundary during the current login attempt. */
    private final ConcurrentHashMap<UUID, String> pendingTestConnections;

    protected PlayerListener(ChampionshipsCore plugin) {
        super(plugin);
        onlinePlayerCount = new AtomicInteger(plugin.getServer().getOnlinePlayers().size());
        Set<NamespacedKey> discovered = new HashSet<>();
        plugin.getServer()
                .recipeIterator()
                .forEachRemaining(
                        recipe -> {
                            if (recipe instanceof Keyed keyedRecipe)
                                discovered.add(keyedRecipe.getKey());
                        });
        recipeKeys = Set.copyOf(discovered);
        pendingTestConnections = new ConcurrentHashMap<>();
        plugin.getRedisManager().setChatReceiver(this::receiveCrossServerChat);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerPresentation presentation = presentation(player);
        event.renderer(
                (source, sourceDisplayName, message, viewer) -> {
                    return presentation.chatLine(
                            player.getName(), ChatMessageText.format(player, message));
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void publishCrossServerChat(AsyncChatEvent event) {
        if (!plugin.getRedisManager().isReady()) return;
        String sourceInstance = plugin.getRedisManager().instanceId();
        if (sourceInstance == null || sourceInstance.isBlank()) return;
        Player player = event.getPlayer();
        PlayerPresentation presentation = presentation(player);
        Component message = ChatMessageText.format(player, event.message());
        plugin.getRedisManager()
                .publishChat(
                        CrossServerChatText.message(
                                sourceInstance,
                                player.getUniqueId(),
                                player.getName(),
                                presentation,
                                message,
                                System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeamMessageCommand(PlayerCommandPreprocessEvent event) {
        String message = TeamChatCommandParser.messageBody(event.getMessage());
        if (message == null) return;
        event.setCancelled(true);
        Player sender = event.getPlayer();
        if (message.isEmpty()) {
            sender.sendMessage(LegacyText.component(MessageConfig.CHAT_TEAM_USAGE));
            return;
        }
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(sender);
        if (team == null) {
            sender.sendMessage(LegacyText.component(MessageConfig.CHAT_TEAM_UNAVAILABLE));
            return;
        }
        PlayerPresentation presentation = presentation(sender);
        Component line =
                LegacyText.component(MessageConfig.CHAT_TEAM_PREFIX)
                        .append(presentation.chatLine(sender.getName(), Component.text(message)));
        team.getOnlinePlayers().forEach(player -> player.sendMessage(line));
    }

    private void receiveCrossServerChat(CrossServerChatMessage message) {
        if (plugin.getPublicChatMuteManager().activeMute(message.senderId()) != null) return;
        final Component line;
        try {
            line = CrossServerChatText.render(message);
        } catch (RuntimeException malformed) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Rejected malformed cross-server chat component " + message.messageId(),
                            malformed);
            return;
        }
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (plugin.getPublicChatMuteManager().activeMute(message.senderId())
                                    != null) return;
                            plugin.getServer()
                                    .getOnlinePlayers()
                                    .forEach(player -> player.sendMessage(line));
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
        TestSessionManager testSessions = plugin.getTestSessionManager();
        TestAuthGateway.PreLoginResult testLogin =
                testSessions == null
                        ? new TestAuthGateway.PreLoginResult(
                                TestAuthGateway.PreLoginStatus.NOT_TEST_IDENTITY,
                                null,
                                "test sessions unavailable")
                        : testSessions.observePreLogin(
                                event.getName(), event.getUniqueId(), java.time.Instant.now());
        if (testLogin.status() == TestAuthGateway.PreLoginStatus.REJECTED) {
            pendingTestConnections.remove(event.getUniqueId());
            event.kickMessage(
                    LegacyComponentSerializer.legacySection()
                            .deserialize(
                                    LegacyText.translateColorCodes(
                                            MessageConfig.IDENTITY_VERIFICATION_FAILED)));
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            return;
        }
        boolean testIdentity = testLogin.status() == TestAuthGateway.PreLoginStatus.ACCEPTED;
        if (testIdentity) pendingTestConnections.put(event.getUniqueId(), event.getName());
        else pendingTestConnections.remove(event.getUniqueId());
        PlayerIdentityMigrationResult migration = null;
        if (!testIdentity) {
            migration =
                    plugin.getPlayerManager().prepareIdentity(event.getName(), event.getUniqueId());
            if (!migration.successful()) {
                event.kickMessage(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(
                                        LegacyText.translateColorCodes(
                                                MessageConfig.IDENTITY_VERIFICATION_FAILED)));
                event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                return;
            }
        }
        ChampionshipTeam championshipTeam =
                plugin.getTeamManager().getTeamByPlayer(event.getUniqueId());
        String name = event.getName();
        boolean hasResolvedTeam =
                migration != null
                        && migration.successful()
                        && !migration.hasTeamConflict()
                        && migration.resolvedTeamId() != null;

        if (onlinePlayerCount.get() >= CCConfig.MAX_PLAYERS) {
            if (CCConfig.WHITELIST.contains(name)) return;

            if (championshipTeam == null && !hasResolvedTeam) {
                event.kickMessage(
                        LegacyComponentSerializer.legacySection()
                                .deserialize(
                                        LegacyText.translateColorCodes(MessageConfig.SERVER_FULL)));
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
        String pendingTestName = pendingTestConnections.remove(player.getUniqueId());
        if (pendingTestName != null && !pendingTestName.equalsIgnoreCase(player.getName())) {
            kickTestIdentity(player);
            return;
        }
        TestSessionManager testSessions = plugin.getTestSessionManager();
        boolean reservedTestIdentity =
                pendingTestName != null
                        && testSessions != null
                        && testSessions.isReservedTestIdentity(
                                player.getName(), player.getUniqueId());
        if (pendingTestName != null && !reservedTestIdentity) {
            // The session may have stopped after pre-login but before Bukkit created the player.
            // Never fall back to the formal identity path for that stale connection.
            kickTestIdentity(player);
            return;
        }
        if (reservedTestIdentity) {
            // A test identity must have crossed AuthMe's LoginEvent before it can become a
            // Bukkit player. Never let this branch create/update a formal Core identity.
            if (testSessions.isAuthenticatedTestIdentity(player.getName(), player.getUniqueId())) {
                if (!admitEphemeralTestIdentity(player, playerManager)) return;
            } else {
                // AuthMe 6 may dispatch LoginEvent after PlayerJoinEvent when the login was
                // completed through its 26.3 dialog. Keep the player out of formal identity and
                // poll for a bounded interval before rejecting the connection; a single fixed
                // delay is too short when 64 AuthMe LoginEvents are dispatched together.
                awaitTestAuthentication(
                        player,
                        testSessions,
                        playerManager,
                        0,
                        System.nanoTime() + TEST_AUTH_CONFIRM_TIMEOUT_NANOS);
            }
        } else {
            playerManager.updatePlayer(player);
        }
        PlayerPresentation presentation = presentation(player);
        event.joinMessage(
                Component.translatable(
                        "multiplayer.player.joined", presentation.identity(player.getName())));

        // Test identities have no formal rank history. Do not replay a production result for them.
        if (!reservedTestIdentity) {
            // Let normal join/teleport notices finish first, then restore a recent result that may
            // have been missed while this player was disconnected.
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (player.isOnline())
                                    plugin.getRankManager().replayRecentRankingSummary(player);
                            },
                            40L);
        }

        player.discoverRecipes(recipeKeys);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void recoverDeveloperStateOnJoin(PlayerJoinEvent event) {
        TestSessionManager testSessions = plugin.getTestSessionManager();
        if (testSessions != null) testSessions.recoverDeveloperStateOnJoin(event.getPlayer());
    }

    private boolean admitEphemeralTestIdentity(Player player, PlayerManager playerManager) {
        try {
            playerManager.prepareEphemeralTestIdentity(player.getName(), player.getUniqueId());
            return true;
        } catch (RuntimeException failure) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Rejected test identity cache collision for "
                                    + player.getName()
                                    + " ("
                                    + player.getUniqueId()
                                    + ")",
                            failure);
            kickTestIdentity(player);
            return false;
        }
    }

    private void kickTestIdentity(Player player) {
        player.kick(
                LegacyComponentSerializer.legacySection()
                        .deserialize(
                                LegacyText.translateColorCodes(
                                        MessageConfig.IDENTITY_VERIFICATION_FAILED)));
    }

    private void awaitTestAuthentication(
            Player player,
            TestSessionManager testSessions,
            PlayerManager playerManager,
            int attempt,
            long deadlineNanos) {
        if (!player.isOnline()) return;
        if (testSessions.isAuthenticatedTestIdentity(player.getName(), player.getUniqueId())) {
            admitEphemeralTestIdentity(player, playerManager);
            return;
        }
        if (attempt >= TEST_AUTH_CONFIRM_RETRY_LIMIT || System.nanoTime() >= deadlineNanos) {
            kickTestIdentity(player);
            return;
        }
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () ->
                                awaitTestAuthentication(
                                        player,
                                        testSessions,
                                        playerManager,
                                        attempt + 1,
                                        deadlineNanos),
                        TEST_AUTH_CONFIRM_RETRY_TICKS);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlinePlayerCount.updateAndGet(current -> Math.max(0, current - 1));
        Player player = event.getPlayer();
        // Do not remove a marker for the same UUID here: an overlapping reconnect can have
        // already replaced it during pre-login. The marker is consumed by the corresponding
        // PlayerJoinEvent, or replaced/cleared by the next pre-login decision.
        PlayerPresentation presentation = presentation(player);
        event.quitMessage(
                Component.translatable(
                        "multiplayer.player.left", presentation.identity(player.getName())));
        if (plugin.getTestSessionManager() != null)
            plugin.getTestSessionManager().onPlayerDisconnect(player);
    }

    private PlayerPresentation presentation(Player player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        BaseGameInstance playerArea =
                plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        boolean daily = plugin.getDailyManager() != null && plugin.getDailyManager().isDailyLobby();
        if (daily) {
            if (plugin.getTeamManager().isTransientTeam(team)) {
                return new PlayerPresentation(
                        team.getColoredName(),
                        team.getColorCode(),
                        playerArea != null,
                        true,
                        MessageConfig.playerTextFormat());
            }
            BaseGameInstance shownArea = playerArea;
            if (shownArea == null)
                shownArea = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            String label =
                    shownArea == null
                            ? MessageConfig.PRESENTATION_DAILY_LOBBY
                            : MessageConfig.PRESENTATION_DAILY_GAME.replace(
                                    "%game%", shownArea.getGameTypeEnum().toString());
            return new PlayerPresentation(
                    label,
                    team == null ? null : team.getColorCode(),
                    playerArea != null && team != null,
                    true,
                    MessageConfig.playerTextFormat());
        }
        String label = team == null ? MessageConfig.PLACEHOLDER_SPECTATOR : team.getColoredName();
        return new PlayerPresentation(
                label,
                team == null ? null : team.getColorCode(),
                playerArea != null && team != null,
                false,
                MessageConfig.playerTextFormat());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player assailant
                && event.getEntity() instanceof Player player) {
            ChampionshipTeam assailantChampionshipTeam =
                    plugin.getTeamManager().getTeamByPlayer(assailant);
            if (assailantChampionshipTeam != null) {
                if (assailantChampionshipTeam.equals(
                        plugin.getTeamManager().getTeamByPlayer(player))) {
                    event.setCancelled(true);
                }
            }
        }

        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Arrow) {
                Projectile projectile = (Projectile) event.getDamager();
                ProjectileSource projectileSource = projectile.getShooter();
                if (!(projectileSource instanceof Player assailant)) return;

                ChampionshipTeam assailantChampionshipTeam =
                        plugin.getTeamManager().getTeamByPlayer(assailant);
                if (assailantChampionshipTeam != null) {
                    if (assailantChampionshipTeam.equals(
                            plugin.getTeamManager().getTeamByPlayer(player))) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}
