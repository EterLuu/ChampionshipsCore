package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsTeamArea;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SkyWarsPlaceholder extends BaseGamePlaceholder<SkyWarsTeamArea> {
    public SkyWarsPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "skywars";
    }

    @Override
    protected BaseAreaManager<SkyWarsTeamArea> getManager() {
        return plugin.getGameManager().getSkyWarsManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_survived_players_")) {
            SkyWarsTeamArea skyWarsTeamArea = resolveArea(params, "area_survived_players_", offlinePlayer);
            if (skyWarsTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(skyWarsTeamArea.getSurvivedPlayerNums());
        }
        if (params.startsWith("area_survived_teams_")) {
            SkyWarsTeamArea skyWarsTeamArea = resolveArea(params, "area_survived_teams_", offlinePlayer);
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
            SkyWarsTeamArea skyWarsTeamArea = resolveArea(params, "player_border_distance_", offlinePlayer);
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
