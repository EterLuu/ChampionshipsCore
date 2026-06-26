package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class TNTRunPlaceholder extends BaseGamePlaceholder<TNTRunTeamArea> {
    public TNTRunPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tntrun";
    }

    @Override
    protected BaseAreaManager<TNTRunTeamArea> getManager() {
        return plugin.getGameManager().getTntRunManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_tnt_rain_countdown_")) {
            TNTRunTeamArea tntRunTeamArea = resolveArea(params, "area_tnt_rain_countdown_", offlinePlayer);
            if (tntRunTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (tntRunTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return String.valueOf(0);
            }

            int timer = tntRunTeamArea.getTimer();
            if (timer >= 120)
                return String.valueOf(timer - 120 + 1);
            if (timer >= 60)
                return String.valueOf(timer - 60 + 1);
            if (timer >= 20)
                return String.valueOf(timer - 20 + 1);

            return String.valueOf(0);
        }
        if (params.startsWith("area_survived_players_")) {
            TNTRunTeamArea tntRunTeamArea = resolveArea(params, "area_survived_players_", offlinePlayer);
            if (tntRunTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(tntRunTeamArea.getSurvivedPlayerNums());
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
