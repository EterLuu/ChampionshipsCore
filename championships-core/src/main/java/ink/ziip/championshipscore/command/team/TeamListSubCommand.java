package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
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
        super("list", "查看全部队伍名单与在线情况", "/cc team list");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }

        List<ChampionshipTeam> teams = new ArrayList<>(plugin.getTeamManager().getTeamList());
        teams.sort(Comparator.comparing(ChampionshipTeam::getName, String.CASE_INSENSITIVE_ORDER));

        if (teams.isEmpty()) {
            sender.sendMessage(Utils.translateColorCodes(MessageConfig.TEAM_NO_TEAMS));
            return true;
        }

        StringBuilder message = new StringBuilder("&#696969&m────────&r &#fff566全部队伍名单 &#696969&m────────");
        int totalMembers = 0;
        int totalOnline = 0;

        for (ChampionshipTeam team : teams) {
            List<String> online = new ArrayList<>();
            List<String> offline = new ArrayList<>();

            for (UUID uuid : team.getMembers()) {
                Player player = Bukkit.getPlayer(uuid);
                String name = player != null ? player.getName() : plugin.getPlayerManager().getPlayerName(uuid);
                if (player != null)
                    online.add(name);
                else
                    offline.add(name);
            }

            online.sort(String.CASE_INSENSITIVE_ORDER);
            offline.sort(String.CASE_INSENSITIVE_ORDER);
            totalMembers += online.size() + offline.size();
            totalOnline += online.size();

            message.append("\n&r").append(team.getColorCode()).append(team.getName())
                    .append(" &#696969(").append(online.size()).append("/")
                    .append(online.size() + offline.size()).append(" 在线)")
                    .append("\n  &#55ff55在线: &f").append(formatNames(online))
                    .append("\n  &#ff6b6b离线: &f").append(formatNames(offline));
        }

        message.append("\n&#696969共 ").append(teams.size()).append(" 支队伍，")
                .append(totalOnline).append("/").append(totalMembers).append(" 名成员在线");
        sender.sendMessage(Utils.translateColorCodes(message.toString()));
        return true;
    }

    private String formatNames(List<String> names) {
        return names.isEmpty() ? "&#696969无" : String.join("&7, &f", names);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
