package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

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
                new StringBuilder(MessageConfig.codeText("team-list-sub-command.on-command-1"));
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

            message.append("\n&r")
                    .append(team.getColorCode())
                    .append(team.getName())
                    .append(" &#696969(")
                    .append(online.size())
                    .append("/")
                    .append(online.size() + offline.size())
                    .append(MessageConfig.codeText("team-list-sub-command.on-command-4"))
                    .append(MessageConfig.codeText("team-list-sub-command.on-command-3"))
                    .append(formatNames(online))
                    .append(MessageConfig.codeText("team-list-sub-command.on-command-2"))
                    .append(formatNames(offline));
        }

        message.append(MessageConfig.codeText("team-list-sub-command.on-command-7"))
                .append(teams.size())
                .append(MessageConfig.codeText("team-list-sub-command.on-command-6"))
                .append(totalOnline)
                .append("/")
                .append(totalMembers)
                .append(MessageConfig.codeText("team-list-sub-command.on-command-5"));
        sender.sendMessage(LegacyText.translateColorCodes(message.toString()));
        return true;
    }

    private String formatNames(List<String> names) {
        return names.isEmpty()
                ? MessageConfig.codeText("team-list-sub-command.format-names-1")
                : String.join("&7, &f", names);
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
