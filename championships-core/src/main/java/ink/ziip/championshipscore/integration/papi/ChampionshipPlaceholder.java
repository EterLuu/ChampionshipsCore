package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class ChampionshipPlaceholder extends BasePlaceholder {
    public ChampionshipPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cc";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params.startsWith("player_team_name_no_color")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_SPECTATOR;

            return championshipTeam.getName();
        }
        if (params.startsWith("player_team_name")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_SPECTATOR;

            return championshipTeam.getColoredName();
        }
        if (params.startsWith("player_team_color_code")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_NONE;

            return championshipTeam.getColorCode();
        }
        if (params.startsWith("player_team_color")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_NONE;

            return championshipTeam.getColorName();
        }

        if (params.startsWith("player_points")) {
            return Utils.formatPoints(plugin.getRankManager().getPlayerPoints(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_team_points")) {
            return Utils.formatPoints(plugin.getRankManager().getPlayerTeamPoints(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_rank")) {
            return String.valueOf(plugin.getRankManager().getPlayerRank(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_team_rank")) {
            return String.valueOf(plugin.getRankManager().getPlayerTeamRank(offlinePlayer.getUniqueId()));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
