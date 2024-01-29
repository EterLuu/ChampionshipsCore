package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SnowballPlaceholder extends BasePlaceholder {
    public SnowballPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "snowball";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        SnowballShowdownManager snowballShowdownManager = plugin.getGameManager().getSnowballShowdownManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            SnowballShowdownTeamArea snowballShowdownTeamArea = snowballShowdownManager.getArea(params.replace("area_status_", ""));
            if (snowballShowdownTeamArea == null) {
                snowballShowdownTeamArea = snowballShowdownManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (snowballShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return snowballShowdownTeamArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            SnowballShowdownTeamArea
                    snowballShowdownTeamArea = snowballShowdownManager.getArea(params.replace("area_timer_", ""));
            if (snowballShowdownTeamArea == null) {
                snowballShowdownTeamArea = snowballShowdownManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (snowballShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(snowballShowdownTeamArea.getTimer() + 1);
        }
        if (params.startsWith("area_rank_")) {
            SnowballShowdownTeamArea
                    snowballShowdownTeamArea = snowballShowdownManager.getArea(params.replace("area_rank_", ""));
            if (snowballShowdownTeamArea == null) {
                snowballShowdownTeamArea = snowballShowdownManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (snowballShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(snowballShowdownTeamArea.getCurrentRank());
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_individual_kills_")) {
            SnowballShowdownTeamArea snowballShowdownTeamArea = snowballShowdownManager.getArea(params.replace("player_individual_kills_", ""));
            if (snowballShowdownTeamArea == null) {
                snowballShowdownTeamArea = snowballShowdownManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (snowballShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(snowballShowdownTeamArea.getPlayerIndividualKills(player));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
