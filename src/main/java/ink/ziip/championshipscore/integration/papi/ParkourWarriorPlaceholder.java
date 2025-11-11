package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorManager;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.api.object.game.parkourwarrior.PKWCheckPointTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ParkourWarriorPlaceholder extends BasePlaceholder {
    public ParkourWarriorPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "parkourwarrior";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        ParkourWarriorManager parkourWarriorManager = plugin.getGameManager().getParkourWarriorManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_name_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("area_name_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return parkourWarriorTeamArea.getGameConfig().getAreaName();
        }
        if (params.startsWith("area_status_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("area_status_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return parkourWarriorTeamArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("area_timer_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getTimer() + 1);
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_main_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("player_main_checkpoints_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.main, 0));
        }
        if (params.startsWith("player_3rd_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("player_3rd_checkpoints_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, 0));
        }
        if (params.startsWith("player_4th_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("player_4th_checkpoints_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, 1));
        }
        if (params.startsWith("player_5th_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("player_5th_checkpoints_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, 2));
        }
        if (params.startsWith("player_total_main_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("player_total_main_checkpoints_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.main, null));
        }
        if (params.startsWith("player_progress_sub_checkpoint_")) {
            String[] splitParams = params.split("_");
            String subCheckpoint = splitParams[splitParams.length - 1];
            ParkourWarriorTeamArea parkourWarriorTeamArea = parkourWarriorManager.getArea(params.replace("_" + subCheckpoint, "").replace("player_progress_sub_checkpoint_", ""));
            if (parkourWarriorTeamArea == null) {
                parkourWarriorTeamArea = parkourWarriorManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, subCheckpoint));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
