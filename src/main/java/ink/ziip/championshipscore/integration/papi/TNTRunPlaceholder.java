package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class TNTRunPlaceholder extends BasePlaceholder {
    public TNTRunPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tntrun";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        TNTRunManager tntRunManager = plugin.getGameManager().getTntRunManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            TNTRunTeamArea tntRunTeamArea = tntRunManager.getArea(params.replace("area_status_", ""));
            if (tntRunTeamArea == null) {
                tntRunTeamArea = tntRunManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tntRunTeamArea == null) {
                return null;
            }
            return tntRunTeamArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            TNTRunTeamArea tntRunTeamArea = tntRunManager.getArea(params.replace("area_timer_", ""));
            if (tntRunTeamArea == null) {
                tntRunTeamArea = tntRunManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tntRunTeamArea == null) {
                return null;
            }
            return String.valueOf(tntRunTeamArea.getTimer() + 1);
        }
        if (params.startsWith("area_tnt_rain_countdown_")) {
            TNTRunTeamArea tntRunTeamArea = tntRunManager.getArea(params.replace("area_tnt_rain_countdown_", ""));
            if (tntRunTeamArea == null) {
                tntRunTeamArea = tntRunManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tntRunTeamArea == null) {
                return null;
            }
            if (tntRunTeamArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return String.valueOf(0);
            }

            int timer = tntRunTeamArea.getTimer();
            if (timer >= 120)
                return String.valueOf(timer - 120 + 1);
            if (timer >= 60)
                return String.valueOf(timer - 60 + 1);

            return String.valueOf(0);
        }
        if (params.startsWith("area_survived_players_")) {
            TNTRunTeamArea tntRunTeamArea = tntRunManager.getArea(params.replace("area_survived_players_", ""));
            if (tntRunTeamArea == null) {
                tntRunTeamArea = tntRunManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tntRunTeamArea == null) {
                return null;
            }
            return String.valueOf(tntRunTeamArea.getSurvivedPlayerNums());
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
