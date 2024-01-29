package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsManager;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsTeamArea;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SkyWarsPlaceholder extends BasePlaceholder {
    public SkyWarsPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "skywars";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        SkyWarsManager skyWarsManager = plugin.getGameManager().getSkyWarsManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            SkyWarsTeamArea skyWarsTeamArea = skyWarsManager.getArea(params.replace("area_status_", ""));
            if (skyWarsTeamArea == null) {
                skyWarsTeamArea = skyWarsManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (skyWarsTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return skyWarsTeamArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            SkyWarsTeamArea skyWarsTeamArea = skyWarsManager.getArea(params.replace("area_timer_", ""));
            if (skyWarsTeamArea == null) {
                skyWarsTeamArea = skyWarsManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (skyWarsTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(skyWarsTeamArea.getTimer() + 1);
        }
        if (params.startsWith("area_survived_players_")) {
            SkyWarsTeamArea skyWarsTeamArea = skyWarsManager.getArea(params.replace("area_survived_players_", ""));
            if (skyWarsTeamArea == null) {
                skyWarsTeamArea = skyWarsManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (skyWarsTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(skyWarsTeamArea.getSurvivedPlayerNums());
        }
        if (params.startsWith("area_survived_teams_")) {
            SkyWarsTeamArea skyWarsTeamArea = skyWarsManager.getArea(params.replace("area_survived_teams_", ""));
            if (skyWarsTeamArea == null) {
                skyWarsTeamArea = skyWarsManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (skyWarsTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(skyWarsTeamArea.getSurvivedTeamNums());
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_border_distance_")) {
            SkyWarsTeamArea skyWarsTeamArea = skyWarsManager.getArea(params.replace("player_border_distance_", ""));
            if (skyWarsTeamArea == null) {
                skyWarsTeamArea = skyWarsManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (skyWarsTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (skyWarsTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return String.valueOf(0);
            }

            return String.valueOf(skyWarsTeamArea.getPlayerBoarderDistance(player));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
