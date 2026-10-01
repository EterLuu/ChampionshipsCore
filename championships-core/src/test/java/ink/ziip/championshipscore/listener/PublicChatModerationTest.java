package ink.ziip.championshipscore.listener;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.chat.PublicChatMuteManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.player.identity.PlayerUuidLookupException;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.command.MainCommand;
import ink.ziip.championshipscore.command.admin.AdminMainCommand;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ConfigurationStateExtension.class)
class PublicChatModerationTest {
    @TempDir Path directory;
    private Object previousPlugin;
    private Object previousServer;
    private ChampionshipsCore plugin;
    private PublicChatMuteManager mutes;
    private TestPlayers identities;
    private MainCommand commands;
    private CommandSender console;
    private Player player;
    private final UUID playerId = UUID.randomUUID();
    private final LinkedBlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
    private final List<String> feedback = new ArrayList<>();
    private final List<String> received = new ArrayList<>();
    private boolean admin = true;
    private boolean offline;
    private Set<String> chatPermissions = Set.of();
    private final Command command = new Command("cc") {
        @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
    };

    @BeforeEach void setup() throws Exception {
        previousPlugin = field(ChampionshipsCore.class, "instance").get(null);
        previousServer = field(Bukkit.class, "server").get(null);
        plugin = allocate(ChampionshipsCore.class);
        field(ChampionshipsCore.class, "instance").set(null, plugin);
        set(plugin, "dataFolder", directory.toFile());
        set(plugin, "logger", Logger.getAnonymousLogger());
        set(plugin, "isEnabled", true);
        player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> playerId;
            case "getName" -> "Muted";
            case "hasPermission" -> chatPermissions.contains(a[0]);
            case "sendMessage" -> {
                received.add(a[0] instanceof Component line ? PlainTextComponentSerializer.plainText().serialize(line) : a[0].toString());
                yield null;
            }
            default -> throw new AssertionError(m.getName());
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> {
            if (m.getName().equals("runTask")) { scheduled.add((Runnable) a[1]); return null; }
            throw new AssertionError(m.getName());
        });
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getScheduler" -> scheduler;
            case "getPlayerExact" -> offline ? null : player;
            case "getOnlinePlayers" -> offline ? List.of() : List.of(player);
            default -> throw new AssertionError(m.getName());
        });
        field(Bukkit.class, "server").set(null, server);
        set(plugin, "server", server);
        identities = allocate(TestPlayers.class);
        identities.id = playerId;
        set(plugin, "playerManager", identities);
        mutes = new PublicChatMuteManager(plugin);
        mutes.load();
        set(plugin, "publicChatMuteManager", mutes);
        console = proxy(CommandSender.class, (p, m, a) -> switch (m.getName()) {
            case "getName" -> "Console";
            case "hasPermission" -> admin;
            case "sendMessage" -> { feedback.add(a[0].toString()); yield null; }
            default -> throw new AssertionError(m.getName());
        });
        MessageConfig.NO_PERMISSION = "denied";
        MessageConfig.COMMAND_USAGE = "usage %usage% %description%";
        MessageConfig.MUTE_DEFAULT_REASON = "default reason";
        MessageConfig.ADMIN_MUTE_SET = "muted %player% %reason%";
        MessageConfig.ADMIN_TEMP_MUTE_SET = "tempmuted %player% %duration% %reason%";
        MessageConfig.ADMIN_UNMUTE_SET = "unmuted %player%";
        MessageConfig.ADMIN_MUTE_NOT_MUTED = "not muted %player%";
        MessageConfig.ADMIN_MUTE_INVALID_DURATION = "invalid duration";
        MessageConfig.ADMIN_MUTE_LOOKUP_FAILED = "lookup failed %player%";
        MessageConfig.ADMIN_MUTE_SAVE_FAILED = "save failed";
        MessageConfig.CHAT_MUTED_PERMANENT = "public chat muted: %reason%";
        MessageConfig.CHAT_MUTED_TEMPORARY = "public chat muted: %remaining% %reason%";
        MessageConfig.CHAT_UNMUTED = "public chat unmuted";
        MessageConfig.CHAT_TEAM_PREFIX = "[Team] ";
        commands = new MainCommand();
        commands.addSubCommand(new AdminMainCommand());
    }

    @AfterEach void cleanup() throws Exception {
        if (mutes != null) mutes.unload();
        field(Bukkit.class, "server").set(null, previousServer);
        field(ChampionshipsCore.class, "instance").set(null, previousPlugin);
    }

    @Test void adminCommandsApplyPermanentAndTemporaryMutesAndRemoveThemForOfflinePlayers() throws Exception {
        offline = true;
        run("mute", "Muted", "spam", "in", "chat");
        awaitCallback();
        assertTrue(mutes.activeMute(playerId).permanent());
        assertEquals("spam in chat", mutes.activeMute(playerId).reason());
        assertEquals("Console", mutes.activeMute(playerId).actor());
        run("tempmute", "Muted", "1h30m");
        awaitCallback();
        var temporary = mutes.activeMute(playerId);
        assertEquals(5_400_000, temporary.expiresAt() - temporary.createdAt());
        assertEquals("default reason", temporary.reason());
        assertEquals(2, identities.calls);
        run("unmute", "muted");
        awaitCallback();
        assertNull(mutes.activeMute(playerId));
        assertEquals(2, identities.calls, "stored offline identities can be unmuted without another profile request");
        assertTrue(feedback.getLast().contains("unmuted"));
    }

    @Test void permissionAndInvalidDurationFailuresNeverResolveOrMuteAPlayer() {
        admin = false;
        run("mute", "Muted");
        assertEquals("denied", feedback.getLast());
        assertFalse(commands.onTabComplete(console, command, "cc", new String[]{"admin", ""}).contains("mute"));
        admin = true;
        assertTrue(commands.onTabComplete(console, command, "cc", new String[]{"admin", ""})
                .containsAll(List.of("mute", "tempmute", "unmute")));
        run("tempmute", "Muted", "0m");
        assertTrue(feedback.getLast().contains("invalid duration"));
        run("tempmute", "Muted", "9223372036854775s");
        assertTrue(feedback.getLast().contains("invalid duration"));
        assertEquals(0, identities.calls);
        assertNull(mutes.activeMute(playerId));
    }

    @Test void publicChatIsCancelledButTeamChatStillReachesTeammates() throws Exception {
        mutes.mute("Muted", 0, "spam", "Console").toCompletableFuture().get(5, TimeUnit.SECONDS);
        var publicChat = chat();
        new PublicChatMuteListener(plugin).onPublicChat(publicChat);
        assertTrue(publicChat.isCancelled());
        awaitCallback();
        assertTrue(received.getLast().contains("public chat muted"));

        var teams = allocate(TestTeams.class);
        teams.team = new TestTeam(player);
        set(plugin, "teamManager", teams);
        set(plugin, "gameManager", allocate(TestGames.class));
        var listener = allocate(PlayerListener.class);
        set(listener, "plugin", plugin);
        var teamChat = new PlayerCommandPreprocessEvent(player, "/teammsg team secret", Set.of());
        listener.onTeamMessageCommand(teamChat);
        assertTrue(received.getLast().contains("team secret"));
        assertNotNull(mutes.activeMute(playerId));

        mutes.unmute("Muted").toCompletableFuture().get(5, TimeUnit.SECONDS);
        publicChat = chat();
        new PublicChatMuteListener(plugin).onPublicChat(publicChat);
        assertFalse(publicChat.isCancelled());
        assertTrue(scheduled.isEmpty());
    }

    @Test void incomingCrossServerPublicChatIsDroppedForMutedSenders() throws Exception {
        mutes.mute("Muted", 0, "spam", "Console").toCompletableFuture().get(5, TimeUnit.SECONDS);
        var listener = allocate(PlayerListener.class);
        set(listener, "plugin", plugin);
        var receive = PlayerListener.class.getDeclaredMethod("receiveCrossServerChat", CrossServerChatMessage.class);
        receive.setAccessible(true);
        receive.invoke(listener, new CrossServerChatMessage(UUID.randomUUID(), "worker", playerId,
                "Muted", "Red", "#ff0000", true, "{}", System.currentTimeMillis()));
        assertTrue(scheduled.isEmpty());
        assertTrue(received.isEmpty());
    }

    @Test void localChatUsesTheServerModeForTeamsAndSpectators() throws Exception {
        var teams = allocate(TestTeams.class);
        set(plugin, "teamManager", teams);
        set(plugin, "gameManager", allocate(TestGames.class));
        var daily = allocate(ink.ziip.championshipscore.api.daily.DailyManager.class);
        set(plugin, "dailyManager", daily);
        MessageConfig.PRESENTATION_DAILY_LOBBY = "大厅";
        MessageConfig.PLACEHOLDER_SPECTATOR = "旁观";
        var listener = allocate(PlayerListener.class);
        set(listener, "plugin", plugin);
        for (boolean dailyMode : new boolean[]{false, true}) {
            set(daily, "serverMode", dailyMode ? ink.ziip.championshipscore.api.object.game.ServerMode.DAILY
                    : ink.ziip.championshipscore.api.object.game.ServerMode.CHAMPIONSHIP);
            for (boolean teamed : new boolean[]{false, true}) {
                teams.team = teamed ? new TestTeam(player) : null;
                var event = chat();
                listener.onPlayerChat(event);
                String rendered = PlainTextComponentSerializer.plainText().serialize(event.renderer()
                        .render(player, Component.text("Muted"), event.message(), player));
                String label = teamed ? "Red" : dailyMode ? "大厅" : "旁观";
                assertEquals(dailyMode ? "[" + label + "] Muted » hello" : "Muted <" + label + "> » hello",
                        rendered);
                chatPermissions = Set.of("cc.admin");
                event.message(Component.text("&c&l&nhello"));
                Component adminLine = event.renderer().render(player, Component.text("Muted"), event.message(), player);
                assertEquals(rendered, PlainTextComponentSerializer.plainText().serialize(adminLine));
                assertEquals("§c§l§nhello", net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(adminLine.children().getLast()));
                chatPermissions = Set.of();
            }
        }
    }

    @Test void identityLookupFailureReportsAnErrorAndLeavesStateUnchanged() throws Exception {
        identities.fail = true;
        run("mute", "Unknown");
        awaitCallback();
        assertTrue(feedback.getLast().contains("lookup failed"));
        assertNull(mutes.activeMute(playerId));
    }

    private AsyncChatEvent chat() {
        return new AsyncChatEvent(true, player, Set.of(), ChatRenderer.defaultRenderer(),
                Component.text("hello"), Component.text("hello"), null);
    }

    private void run(String... args) {
        var full = new ArrayList<>(List.of("admin"));
        full.addAll(List.of(args));
        commands.onCommand(console, command, "cc", full.toArray(String[]::new));
    }

    private void awaitCallback() throws InterruptedException {
        Runnable callback = scheduled.poll(5, TimeUnit.SECONDS);
        assertNotNull(callback, "moderation completion must return to the server thread");
        callback.run();
    }

    private static final class TestPlayers extends PlayerManager {
        UUID id;
        int calls;
        boolean fail;
        private TestPlayers() { super(null); }
        @Override public CompletionStage<UUID> resolvePlayerUUID(String name) {
            calls++;
            return fail ? CompletableFuture.failedFuture(new PlayerUuidLookupException(
                    PlayerUuidLookupException.Reason.PLAYER_NOT_FOUND, "not found")) : CompletableFuture.completedFuture(id);
        }
    }

    private static final class TestTeams extends TeamManager {
        ChampionshipTeam team;
        private TestTeams() { super(null); }
        @Override public ChampionshipTeam getTeamByPlayer(Player player) { return team; }
        @Override public boolean isTransientTeam(ChampionshipTeam team) { return team != null; }
    }

    private static final class TestTeam extends ChampionshipTeam {
        private final Player player;
        TestTeam(Player player) { super(1, "Red", "Red", "#ff0000", null); this.player = player; }
        @Override public List<Player> getOnlinePlayers() { return List.of(player); }
    }

    private static final class TestGames extends GameManager {
        private TestGames() { super(null); }
        @Override public BaseGameInstance getBasePlayerArea(UUID id) { return null; }
        @Override public BaseGameInstance getPlayerSpectatorStatus(UUID id) { return null; }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null)).allocateInstance(type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
