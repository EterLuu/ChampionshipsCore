package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSTeamArea;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TGTTOSPlaceholder extends BasePlaceholder {
    public TGTTOSPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tgttos";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        TGTTOSManager tgttosManager = plugin.getGameManager().getTgttosManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            TGTTOSTeamArea tgttosTeamArea = tgttosManager.getArea(params.replace("area_status_", ""));
            if (tgttosTeamArea == null) {
                tgttosTeamArea = tgttosManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tgttosTeamArea == null) {
                return null;
            }
            return tgttosTeamArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            TGTTOSTeamArea tgttosTeamArea = tgttosManager.getArea(params.replace("area_timer_", ""));
            if (tgttosTeamArea == null) {
                tgttosTeamArea = tgttosManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tgttosTeamArea == null) {
                return null;
            }
            return String.valueOf(tgttosTeamArea.getTimer() + 1);
        }
        if (params.startsWith("area_player_arrived_")) {
            TGTTOSTeamArea tgttosTeamArea = tgttosManager.getArea(params.replace("area_player_arrived_", ""));
            if (tgttosTeamArea == null) {
                tgttosTeamArea = tgttosManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tgttosTeamArea == null) {
                return null;
            }

            return String.valueOf(tgttosTeamArea.getArrivedPlayerNums());
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return null;

        if (params.startsWith("player_team_not_arrived_")) {
            TGTTOSTeamArea tgttosTeamArea = tgttosManager.getArea(params.replace("player_team_not_arrived_", ""));
            if (tgttosTeamArea == null) {
                tgttosTeamArea = tgttosManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (tgttosTeamArea == null) {
                return null;
            }
            return String.valueOf(tgttosTeamArea.getPlayerTeamNotArrived(player));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
