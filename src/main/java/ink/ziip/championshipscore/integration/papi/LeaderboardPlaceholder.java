package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public class LeaderboardPlaceholder extends BasePlaceholder {
    public LeaderboardPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "leaderboard";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        RankManager rankManager = plugin.getRankManager();

        /* Non-Player required placeholders */

        if (params.startsWith("player_")) {
            try {
                int num = Integer.parseInt(params.replace("player_", ""));

                Map.Entry<UUID, Double> playerEntry = rankManager.getPlayerLeaderboard().get(num - 1);
                String name = plugin.getPlayerManager().getPlayerName(playerEntry.getKey());
                if (name == null)
                    return MessageConfig.PLACEHOLDER_NONE;

                return MessageConfig.RANK_PLAYER_BOARD_ROW
                        .replace("%player_rank%. ", "")
                        .replace("%player%", name)
                        .replace("%player_point%", String.valueOf(playerEntry.getValue()));
            } catch (Exception ignored) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
        }
        if (params.startsWith("team_")) {
            try {
                int num = Integer.parseInt(params.replace("team_", ""));

                Map.Entry<ChampionshipTeam, Double> teamEntry = rankManager.getTeamLeaderboard().get(num - 1);
                return MessageConfig.RANK_TEAM_BOARD_ROW
                        .replace("%team_rank%. ", "")
                        .replace("%team%", teamEntry.getKey().getColoredName())
                        .replace("%team_point%", String.valueOf(teamEntry.getValue()));
            } catch (Exception ignored) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
