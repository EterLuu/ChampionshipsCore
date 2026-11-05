package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.entry.TeamImportEntry;
import ink.ziip.championshipscore.api.test.TestRoster;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.TeamColors;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Replaces the formal roster with the fixed resident Bot test roster. */
public final class TeamTestSubCommand extends BaseSubCommand {
    private static final String CONFIRM = "--confirm";
    private static final String[] COLOR_CODES = {
        "&f", "&6", "&d", "&b", "&e", "&a", "&d", "&7",
        "&7", "&3", "&5", "&9", "&4", "&2", "&c", "&0"
    };

    public TeamTestSubCommand() {
        super(
                "test",
                MessageConfig.codeText("team-test-sub-command.constructor-1"),
                MessageConfig.codeText("team-test-sub-command.constructor-2"),
                TEST_PERMISSION);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length != 1 || !CONFIRM.equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return true;
        }

        List<String> usernames = TestRoster.defaultBotUsernames();
        List<TeamImportEntry> teams = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String[] colors = TeamColors.names();
        for (int teamIndex = 0; teamIndex < TestRoster.TEAM_COUNT; teamIndex++) {
            List<TeamImportEntry.Member> members = new ArrayList<>();
            for (int seat = 0; seat < TestRoster.SEATS_PER_TEAM; seat++) {
                String username = usernames.get(teamIndex * TestRoster.SEATS_PER_TEAM + seat);
                Player player = onlinePlayer(username);
                if (player == null) {
                    missing.add(username);
                } else {
                    members.add(new TeamImportEntry.Member(player.getUniqueId(), player.getName()));
                }
            }
            teams.add(
                    new TeamImportEntry(
                            String.format(Locale.ROOT, "test%02d", teamIndex + 1),
                            colors[teamIndex],
                            COLOR_CODES[teamIndex],
                            members));
        }
        if (!missing.isEmpty()) {
            CoreMessages.sendAdminError(
                    sender,
                    MessageConfig.codeText(
                            "team-test-sub-command.missing",
                            missing.size(),
                            String.join(", ", missing)));
            return true;
        }

        plugin.getTeamManager()
                .replaceFormalTeams(teams)
                .whenComplete(
                        (replaced, failure) -> {
                            Runnable response =
                                    () -> {
                                        if (failure != null || !Boolean.TRUE.equals(replaced)) {
                                            CoreMessages.sendAdminError(
                                                    sender,
                                                    MessageConfig.codeText(
                                                            "team-test-sub-command.failed",
                                                            failure == null
                                                                    ? MessageConfig.codeText(
                                                                            "team-test-sub-command.busy")
                                                                    : failure.getMessage()));
                                        } else {
                                            CoreMessages.sendAdminSuccess(
                                                    sender,
                                                    MessageConfig.codeText(
                                                            "team-test-sub-command.completed",
                                                            TestRoster.TOTAL_SEATS,
                                                            TestRoster.TEAM_COUNT));
                                        }
                                    };
                            if (Bukkit.isPrimaryThread()) response.run();
                            else if (plugin.isEnabled())
                                plugin.getServer().getScheduler().runTask(plugin, response);
                        });
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        return args.length == 1 ? complete(List.of(CONFIRM), args[0]) : List.of();
    }

    private static Player onlinePlayer(String username) {
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getName().equalsIgnoreCase(username)) return player;
        return null;
    }
}
