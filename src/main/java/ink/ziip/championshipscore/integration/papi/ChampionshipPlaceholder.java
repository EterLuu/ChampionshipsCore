package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
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

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_points")) {

            return String.valueOf(plugin.getRankManager().getPlayerPoints(player));
        }
        if (params.startsWith("player_team_points")) {

            return String.valueOf(plugin.getRankManager().getPlayerTeamPoints(player));
        }
        if (params.startsWith("player_rank")) {

            return String.valueOf(plugin.getRankManager().getPlayerRank(player));
        }
        if (params.startsWith("player_team_rank")) {

            return String.valueOf(plugin.getRankManager().getPlayerTeamRank(player));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
