package ink.ziip.championshipscore.command.game;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.test.TestEventPlan;
import ink.ziip.championshipscore.api.test.TestEventProfileLoader;
import ink.ziip.championshipscore.api.test.TestRoster;
import ink.ziip.championshipscore.api.test.TestSession;
import ink.ziip.championshipscore.api.test.TestSessionManager;
import ink.ziip.championshipscore.api.test.TestSessionMode;
import ink.ziip.championshipscore.api.test.TestSessionPlan;
import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.protocol.BingoDifficulty;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Controls the Core-owned test session without touching formal event state. */
public final class TestMainCommand extends BaseMainCommand {
    private static final String DEFAULT_PROFILE = "idle";

    public TestMainCommand() {
        super(
                "test",
                MessageConfig.codeText("test-session.description"),
                TEST_AUTOMATION_PERMISSION);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        TestSessionManager manager = plugin.getTestSessionManager();
        try {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "single" -> startSingle(sender, manager, args);
                case "event" -> startEvent(sender, manager, args);
                case "lease" -> registerLease(sender, manager, args);
                case "join" -> join(sender, manager, args);
                case "leave" -> leave(sender, manager, args);
                case "spectate" -> spectate(sender, manager, args);
                case "status" -> status(sender, manager);
                case "run" -> run(sender, manager, args);
                case "stop" -> stop(sender, manager, args);
                default -> sendUsage(sender);
            }
        } catch (RuntimeException failure) {
            CoreMessages.sendAdminError(
                    sender, MessageConfig.codeText("test-session.error", failure.getMessage()));
            sendUsage(sender);
        }
        return true;
    }

    private void startSingle(CommandSender sender, TestSessionManager manager, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("start")) {
            sendUsage(sender);
            return;
        }
        String profile = args.length >= 5 ? args[4] : DEFAULT_PROFILE;
        BingoVariantRules variant = parseVariant(args, 5);
        TestSession session =
                manager.createSession(
                        TestSessionMode.SINGLE_GAME,
                        defaultRoster(),
                        new TestSessionPlan(args[2], args[3], profile, variant));
        CoreMessages.sendAdminSuccess(
                sender, MessageConfig.codeText("test-session.created", session.sessionId()));
    }

    private void startEvent(CommandSender sender, TestSessionManager manager, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("start")) {
            sendUsage(sender);
            return;
        }
        String profile = args[2];
        TestEventPlan eventPlan = new TestEventProfileLoader(plugin).load(profile);
        BingoVariantRules variant = parseVariant(args, 3);
        if (variant != BingoVariantRules.FIXED_POINTS)
            eventPlan =
                    new TestEventPlan(
                            eventPlan.profile(),
                            eventPlan.rounds().stream()
                                    .map(
                                            round ->
                                                    new TestEventPlan.Round(
                                                            round.game(), round.map(), variant))
                                    .toList());
        TestSession session =
                manager.createEventSession(
                        defaultRoster(),
                        new TestSessionPlan(profile, null, DEFAULT_PROFILE),
                        eventPlan);
        CoreMessages.sendAdminSuccess(
                sender, MessageConfig.codeText("test-session.created", session.sessionId()));
    }

    private void registerLease(CommandSender sender, TestSessionManager manager, String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return;
        }
        if (args[1].equalsIgnoreCase("web")) {
            manager.registerWebLeases()
                    .whenComplete(
                            (count, failure) -> {
                                Runnable feedback =
                                        () -> {
                                            if (failure == null)
                                                CoreMessages.sendAdminSuccess(
                                                        sender,
                                                        MessageConfig.codeText(
                                                                "test-session.lease-registered",
                                                                "cc-web "
                                                                        + count
                                                                        + " Bot lease(s)"));
                                            else
                                                CoreMessages.sendAdminError(
                                                        sender,
                                                        MessageConfig.codeText(
                                                                "test-session.error",
                                                                failure.getMessage()));
                                        };
                                if (org.bukkit.Bukkit.isPrimaryThread()) feedback.run();
                                else if (plugin.isEnabled())
                                    plugin.getServer().getScheduler().runTask(plugin, feedback);
                            });
            return;
        }
        manager.registerSignedLease(args[1], Instant.now());
        CoreMessages.sendAdminSuccess(
                sender, MessageConfig.codeText("test-session.lease-registered", "signed token"));
    }

    private void join(CommandSender sender, TestSessionManager manager, String[] args) {
        if (!(sender instanceof Player player) || args.length != 2)
            throw new IllegalArgumentException("usage: /cc game test join <team>");
        manager.joinDeveloper(Integer.parseInt(args[1]), player);
        CoreMessages.sendAdminSuccess(
                sender, MessageConfig.codeText("test-session.developer-joined", args[1]));
    }

    private void leave(CommandSender sender, TestSessionManager manager, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1)
            throw new IllegalArgumentException("usage: /cc game test leave");
        String bot = manager.leaveDeveloper(findDeveloperTeam(manager, player), player);
        CoreMessages.sendAdminSuccess(
                sender, MessageConfig.codeText("test-session.developer-left", bot));
    }

    private void spectate(CommandSender sender, TestSessionManager manager, String[] args) {
        if (!(sender instanceof Player player) || (args.length != 1 && args.length != 2))
            throw new IllegalArgumentException("usage: /cc game test spectate [leave]");
        if (args.length == 2) {
            if (!args[1].equalsIgnoreCase("leave"))
                throw new IllegalArgumentException("usage: /cc game test spectate [leave]");
            if (!manager.leaveDeveloperSpectator(player))
                throw new IllegalStateException("you are not spectating the test game");
            CoreMessages.sendAdminSuccess(
                    sender, MessageConfig.codeText("test-session.spectator-left"));
            return;
        }
        if (!manager.spectateDeveloper(player))
            throw new IllegalStateException("test game has no available spectator instance");
        CoreMessages.sendAdminSuccess(
                sender, MessageConfig.codeText("test-session.spectator-joined"));
    }

    private void status(CommandSender sender, TestSessionManager manager) {
        TestSession session = manager.sessionForStatus();
        if (session == null) {
            CoreMessages.sendAdminInfo(sender, MessageConfig.codeText("test-session.none"));
            return;
        }
        long bound =
                session.roster().snapshot().stream()
                        .filter(seat -> seat.occupantUuid() != null)
                        .count();
        CoreMessages.sendAdminInfo(
                sender,
                MessageConfig.codeText(
                        "test-session.status",
                        session.sessionId(),
                        session.mode(),
                        session.state(),
                        bound,
                        manager.transientTeams().size(),
                        manager.eventRound(),
                        manager.eventStandings().size()));
    }

    private void stop(CommandSender sender, TestSessionManager manager, String[] args) {
        if (args.length != 2 || !args[1].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return;
        }
        manager.closeActiveSessionAsync()
                .whenComplete(
                        (ignored, failure) -> {
                            Runnable feedback =
                                    () -> {
                                        if (failure == null)
                                            CoreMessages.sendAdminSuccess(
                                                    sender,
                                                    MessageConfig.codeText("test-session.stopped"));
                                        else
                                            CoreMessages.sendAdminError(
                                                    sender,
                                                    MessageConfig.codeText(
                                                            "test-session.error",
                                                            "cleanup incomplete: "
                                                                    + failure.getMessage()));
                                    };
                            if (org.bukkit.Bukkit.isPrimaryThread()) feedback.run();
                            else if (plugin.isEnabled())
                                plugin.getServer().getScheduler().runTask(plugin, feedback);
                        });
    }

    private void run(CommandSender sender, TestSessionManager manager, String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return;
        }
        manager.startPlannedGame()
                .whenComplete(
                        (result, failure) -> {
                            Runnable feedback =
                                    () -> {
                                        if (failure != null || result == null || !result.started())
                                            CoreMessages.sendAdminError(
                                                    sender,
                                                    MessageConfig.codeText(
                                                            "test-session.run-failed",
                                                            failure == null
                                                                    ? result == null
                                                                            ? "unknown"
                                                                            : result.detail()
                                                                    : failure.getMessage()));
                                        else
                                            CoreMessages.sendAdminSuccess(
                                                    sender,
                                                    MessageConfig.codeText(
                                                            "test-session.run-started",
                                                            result.detail()));
                                    };
                            if (org.bukkit.Bukkit.isPrimaryThread()) feedback.run();
                            else if (plugin.isEnabled()) {
                                try {
                                    plugin.getServer().getScheduler().runTask(plugin, feedback);
                                } catch (RuntimeException schedulingFailure) {
                                    plugin.getLogger()
                                            .log(
                                                    java.util.logging.Level.FINE,
                                                    "Test start feedback was skipped during"
                                                            + " shutdown",
                                                    schedulingFailure);
                                }
                            }
                        });
    }

    private static int findDeveloperTeam(TestSessionManager manager, Player player) {
        TestSession session = manager.activeSession();
        if (session == null) throw new IllegalStateException("no test session");
        return session.roster().snapshot().stream()
                .filter(seat -> seat.occupantType() == TestRoster.OccupantType.DEVELOPER)
                .filter(seat -> player.getUniqueId().equals(seat.occupantUuid()))
                .map(TestRoster.SeatSnapshot::teamNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("you do not occupy a test seat"));
    }

    private static TestRoster defaultRoster() {
        return TestRoster.fromBotUsernames(TestRoster.defaultBotUsernames());
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 1)
            return filterStartsWith(
                    List.of(
                            "single",
                            "event",
                            "lease",
                            "join",
                            "leave",
                            "spectate",
                            "status",
                            "run",
                            "stop"),
                    args[0]);
        if (args[0].equalsIgnoreCase("single") && args.length == 2)
            return filterStartsWith(List.of("start"), args[1]);
        if (args[0].equalsIgnoreCase("event") && args.length == 2)
            return filterStartsWith(List.of("start"), args[1]);
        if (args[0].equalsIgnoreCase("stop") && args.length == 2)
            return filterStartsWith(List.of("--confirm"), args[1]);
        if (args[0].equalsIgnoreCase("lease") && args.length == 2)
            return filterStartsWith(List.of("web"), args[1]);
        if (args[0].equalsIgnoreCase("join") && args.length == 2)
            return filterStartsWith(
                    java.util.stream.IntStream.rangeClosed(1, TestRoster.TEAM_COUNT)
                            .mapToObj(Integer::toString)
                            .toList(),
                    args[1]);
        if (args[0].equalsIgnoreCase("spectate") && args.length == 2)
            return filterStartsWith(List.of("leave"), args[1]);
        if (args[0].equalsIgnoreCase("single")) return completeSingle(args);
        if (args[0].equalsIgnoreCase("event")) return completeEvent(args);
        return List.of();
    }

    private List<String> completeSingle(String[] args) {
        if (args.length == 3) return filterStartsWith(enabledGameNames(), args[2]);
        if (args.length < 4) return List.of();
        GameTypeEnum game = GameTypeEnum.fromCommand(args[2]);
        if (game == null || !plugin.getGameManager().isGameEnabled(game)) return List.of();
        if (args.length == 4) return filterStartsWith(enabledAreaNames(game), args[3]);
        if (args.length == 5) return filterStartsWith(List.of(DEFAULT_PROFILE), args[4]);
        if (game != GameTypeEnum.Bingo) return List.of();
        if (args.length == 6)
            return filterStartsWith(
                    Arrays.stream(BingoDifficulty.values()).map(Enum::name).toList(), args[5]);
        if (args.length == 7)
            return filterStartsWith(
                    Arrays.stream(BingoRemix.values()).map(Enum::name).toList(), args[6]);
        return List.of();
    }

    private List<String> completeEvent(String[] args) {
        if (args.length == 3) return filterStartsWith(eventProfiles(), args[2]);
        if (args.length == 4)
            return filterStartsWith(
                    Arrays.stream(BingoDifficulty.values()).map(Enum::name).toList(), args[3]);
        if (args.length == 5)
            return filterStartsWith(
                    Arrays.stream(BingoRemix.values()).map(Enum::name).toList(), args[4]);
        return List.of();
    }

    private List<String> eventProfiles() {
        java.util.Set<String> profiles = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        profiles.add("smoke");
        java.io.File directory = new java.io.File(plugin.getDataFolder(), "test/events");
        java.io.File[] files = directory.listFiles((file, name) -> name.endsWith(".yml"));
        if (files != null)
            for (java.io.File file : files)
                profiles.add(file.getName().substring(0, file.getName().length() - 4));
        return new ArrayList<>(profiles);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(
                LegacyText.translateColorCodes(
                        MessageConfig.COMMAND_USAGE
                                .replace("%usage%", MessageConfig.codeText("test-session.usage"))
                                .replace(
                                        "%description%",
                                        MessageConfig.codeText("test-session.description"))));
    }

    private static BingoVariantRules parseVariant(String[] args, int offset) {
        if (args.length <= offset) return BingoVariantRules.FIXED_POINTS;
        if (args.length > offset + 2)
            throw new IllegalArgumentException("usage: [profile] [difficulty] [remix]");
        try {
            BingoDifficulty difficulty =
                    BingoDifficulty.valueOf(args[offset].trim().toUpperCase(java.util.Locale.ROOT));
            BingoRemix remix =
                    args.length == offset + 2
                            ? BingoRemix.valueOf(
                                    args[offset + 1].trim().toUpperCase(java.util.Locale.ROOT))
                            : BingoRemix.NONE;
            return new BingoVariantRules(BingoMode.POINTS, difficulty, 1, remix);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "invalid Bingo test variant; use difficulty and remix", failure);
        }
    }
}
