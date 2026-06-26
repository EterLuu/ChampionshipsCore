package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class DragonEggCarnivalPlaceholder extends BaseGamePlaceholder<DragonEggCarnivalArea> {
    public DragonEggCarnivalPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "decarnival";
    }

    @Override
    protected BaseAreaManager<DragonEggCarnivalArea> getManager() {
        return plugin.getGameManager().getDragonEggCarnivalManager();
    }

    @Override
    protected String areaTimer(DragonEggCarnivalArea area) {
        if (area.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return String.valueOf(0);
        }
        return String.valueOf(area.getTimer() - 1);
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_team_wins_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_team_wins_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(dragonEggCarnivalArea.getLeftTeamPoints());
        }
        if (params.startsWith("area_team_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_team_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = dragonEggCarnivalArea.getLeftChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getName();
        }
        if (params.startsWith("area_rival_wins_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_rival_wins_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(dragonEggCarnivalArea.getRightTeamPoints());
        }
        if (params.startsWith("area_rival_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_rival_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = dragonEggCarnivalArea.getRightChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getName();
        }
        if (params.startsWith("playtool_countdown_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "playtool_countdown_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return String.valueOf(0);
            }

            return String.valueOf(11 - dragonEggCarnivalArea.getTimer() % 10);
        }
        if (params.startsWith("egg_spawn_countdown_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "egg_spawn_countdown_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return String.valueOf(0);
            }

            int time = 101 - dragonEggCarnivalArea.getTimer();
            if (time >= 0)
                return String.valueOf(time);
            return String.valueOf(0);
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
