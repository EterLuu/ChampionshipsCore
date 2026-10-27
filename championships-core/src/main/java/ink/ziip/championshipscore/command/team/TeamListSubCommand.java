package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamListSubCommand extends BaseSubCommand {
    public TeamListSubCommand() {
        super(
                "list",
                MessageConfig.codeText("team-list-sub-command.constructor-1"),
                "/cc team list");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }

        List<ChampionshipTeam> teams = new ArrayList<>(plugin.getTeamManager().getTeamList());
        teams.sort(Comparator.comparing(ChampionshipTeam::getName, String.CASE_INSENSITIVE_ORDER));

        if (teams.isEmpty()) {
            sender.sendMessage(LegacyText.translateColorCodes(MessageConfig.TEAM_NO_TEAMS));
            return true;
        }

        StringBuilder message =
                new StringBuilder(MessageConfig.codeText("team-list-sub-command.header"));
        int totalMembers = 0;
        int totalOnline = 0;

        for (ChampionshipTeam team : teams) {
            List<String> online = new ArrayList<>();
            List<String> offline = new ArrayList<>();

            for (UUID uuid : team.getMembers()) {
                Player player = Bukkit.getPlayer(uuid);
                String name =
                        player != null
                                ? player.getName()
                                : plugin.getPlayerManager().getPlayerName(uuid);
                if (player != null) online.add(name);
                else offline.add(name);
            }

            online.sort(String.CASE_INSENSITIVE_ORDER);
            offline.sort(String.CASE_INSENSITIVE_ORDER);
            totalMembers += online.size() + offline.size();
            totalOnline += online.size();

            message.append(formatTeam(team.getColorCode() + team.getName(), online, offline));
        }

        message.append(formatSummary(teams.size(), totalOnline, totalMembers));
        sender.sendMessage(LegacyText.translateColorCodes(message.toString()));
        return true;
    }

    static String formatTeam(String coloredName, List<String> online, List<String> offline) {
        return MessageTemplate.render(
                MessageConfig.codeText("team-list-sub-command.row"),
                Map.of(
                        "%team%", coloredName,
                        "%online-count%", Integer.toString(online.size()),
                        "%member-count%", Integer.toString(online.size() + offline.size()),
                        "%online-names%", formatNames(online),
                        "%offline-names%", formatNames(offline)));
    }

    static String formatSummary(int teams, int online, int members) {
        return MessageTemplate.render(
                MessageConfig.codeText("team-list-sub-command.summary"),
                Map.of(
                        "%teams%", Integer.toString(teams),
                        "%online%", Integer.toString(online),
                        "%members%", Integer.toString(members)));
    }

    private static String formatNames(List<String> names) {
        return names.isEmpty()
                ? MessageConfig.codeText("team-list-sub-command.empty")
                : String.join(
                        MessageConfig.codeText("team-list-sub-command.names-separator"), names);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        return Collections.emptyList();
    }
}
