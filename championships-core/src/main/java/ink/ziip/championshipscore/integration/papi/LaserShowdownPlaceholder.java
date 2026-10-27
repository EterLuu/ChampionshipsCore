package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.lasershowdown.runtime.LaserShowdownTeamArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LaserShowdownPlaceholder extends BaseGamePlaceholder<LaserShowdownTeamArea> {
    public LaserShowdownPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lasershowdown";
    }

    @Override
    protected BaseGameInstanceManager<LaserShowdownTeamArea> getManager() {
        return plugin.getGameManager().getLaserShowdownManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_rank_1_")) {
            LaserShowdownTeamArea laserShowdownTeamArea =
                    resolveArea(params, "area_rank_1_", offlinePlayer);
            if (laserShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            try {
                return laserShowdownTeamArea.getCurrentRank().get(0)
                        + " | "
                        + laserShowdownTeamArea.getCurrentRank().get(1);
            } catch (Exception ignored) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
        }
        if (params.startsWith("area_rank_2_")) {
            LaserShowdownTeamArea laserShowdownTeamArea =
                    resolveArea(params, "area_rank_2_", offlinePlayer);
            if (laserShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            try {
                return laserShowdownTeamArea.getCurrentRank().get(2)
                        + " | "
                        + laserShowdownTeamArea.getCurrentRank().get(3);
            } catch (Exception ignored) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_individual_kills_")) {
            LaserShowdownTeamArea laserShowdownTeamArea =
                    resolveArea(params, "player_individual_kills_", offlinePlayer);
            if (laserShowdownTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(laserShowdownTeamArea.getPlayerIndividualKills(player));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
