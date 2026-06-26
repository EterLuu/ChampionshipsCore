package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SnowballPlaceholder extends BaseGamePlaceholder<SnowballShowdownTeamArea> {
    public SnowballPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "snowball";
    }

    @Override
    protected BaseAreaManager<SnowballShowdownTeamArea> getManager() {
        return plugin.getGameManager().getSnowballShowdownManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_rank_1_")) {
            SnowballShowdownTeamArea snowballShowdownTeamArea = resolveArea(params, "area_rank_1_", offlinePlayer);
            if (snowballShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            try {
                return snowballShowdownTeamArea.getCurrentRank().get(0) + " | " + snowballShowdownTeamArea.getCurrentRank().get(1);
            } catch (Exception ignored) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
        }
        if (params.startsWith("area_rank_2_")) {
            SnowballShowdownTeamArea snowballShowdownTeamArea = resolveArea(params, "area_rank_2_", offlinePlayer);
            if (snowballShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            try {
                return snowballShowdownTeamArea.getCurrentRank().get(2) + " | " + snowballShowdownTeamArea.getCurrentRank().get(3);
            } catch (Exception ignored) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_individual_kills_")) {
            SnowballShowdownTeamArea snowballShowdownTeamArea = resolveArea(params, "player_individual_kills_", offlinePlayer);
            if (snowballShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(snowballShowdownTeamArea.getPlayerIndividualKills(player));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
