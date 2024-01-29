package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class DragonEggCarnivalPlaceholder extends BasePlaceholder {
    public DragonEggCarnivalPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "decarnival";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        DragonEggCarnivalManager dragonEggCarnivalManager = plugin.getGameManager().getDragonEggCarnivalManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("area_status_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return dragonEggCarnivalArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_team_wins_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("area_team_wins_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(dragonEggCarnivalArea.getLeftTeamPoints());
        }
        if (params.startsWith("area_team_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("area_team_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = dragonEggCarnivalArea.getLeftChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getColoredName();
        }
        if (params.startsWith("area_rival_wins_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("area_rival_wins_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(dragonEggCarnivalArea.getRightTeamPoints());
        }
        if (params.startsWith("area_rival_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("area_rival_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = dragonEggCarnivalArea.getRightChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getColoredName();
        }
        if (params.startsWith("area_timer_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("area_timer_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return String.valueOf(0);
            }

            return String.valueOf(dragonEggCarnivalArea.getTimer() - 1);
        }
        if (params.startsWith("playtool_countdown_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("playtool_countdown_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                return String.valueOf(0);
            }

            return String.valueOf(11 - dragonEggCarnivalArea.getTimer() % 10);
        }
        if (params.startsWith("egg_spawn_countdown_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(params.replace("egg_spawn_countdown_", ""));
            if (dragonEggCarnivalArea == null) {
                dragonEggCarnivalArea = dragonEggCarnivalManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
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
