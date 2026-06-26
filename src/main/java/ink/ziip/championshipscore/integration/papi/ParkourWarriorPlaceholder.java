package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.api.object.game.parkourwarrior.PKWCheckPointTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ParkourWarriorPlaceholder extends BaseGamePlaceholder<ParkourWarriorTeamArea> {
    public ParkourWarriorPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "parkourwarrior";
    }

    @Override
    protected BaseAreaManager<ParkourWarriorTeamArea> getManager() {
        return plugin.getGameManager().getParkourWarriorManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_name_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = resolveArea(params, "area_name_", offlinePlayer);
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return parkourWarriorTeamArea.getGameConfig().getAreaName();
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_main_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = resolveArea(params, "player_main_checkpoints_", offlinePlayer);
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.main, 0));
        }
        if (params.startsWith("player_3rd_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = resolveArea(params, "player_3rd_checkpoints_", offlinePlayer);
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, 0));
        }
        if (params.startsWith("player_4th_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = resolveArea(params, "player_4th_checkpoints_", offlinePlayer);
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, 1));
        }
        if (params.startsWith("player_5th_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = resolveArea(params, "player_5th_checkpoints_", offlinePlayer);
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerSubCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, 2));
        }
        if (params.startsWith("player_total_main_checkpoints_")) {
            ParkourWarriorTeamArea parkourWarriorTeamArea = resolveArea(params, "player_total_main_checkpoints_", offlinePlayer);
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.main, null));
        }
        if (params.startsWith("player_progress_sub_checkpoint_")) {
            String[] splitParams = params.split("_");
            String subCheckpoint = splitParams[splitParams.length - 1];
            String areaName = params.replace("_" + subCheckpoint, "").replace("player_progress_sub_checkpoint_", "");
            ParkourWarriorTeamArea parkourWarriorTeamArea = resolveAreaByName(areaName, offlinePlayer);
            if (parkourWarriorTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(parkourWarriorTeamArea.getPlayerCheckpoints(offlinePlayer.getUniqueId(), PKWCheckPointTypeEnum.sub, subCheckpoint));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
