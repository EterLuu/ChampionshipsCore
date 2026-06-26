package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSTeamArea;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TGTTOSPlaceholder extends BaseGamePlaceholder<TGTTOSTeamArea> {
    public TGTTOSPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tgttos";
    }

    @Override
    protected BaseAreaManager<TGTTOSTeamArea> getManager() {
        return plugin.getGameManager().getTgttosManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_name_")) {
            TGTTOSTeamArea tgttosTeamArea = resolveArea(params, "area_name_", offlinePlayer);
            if (tgttosTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return tgttosTeamArea.getGameConfig().getAreaName();
        }
        if (params.startsWith("area_player_arrived_")) {
            TGTTOSTeamArea tgttosTeamArea = resolveArea(params, "area_player_arrived_", offlinePlayer);
            if (tgttosTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }

            return String.valueOf(tgttosTeamArea.getArrivedPlayerNums());
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_team_not_arrived_")) {
            TGTTOSTeamArea tgttosTeamArea = resolveArea(params, "player_team_not_arrived_", offlinePlayer);
            if (tgttosTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(tgttosTeamArea.getPlayerTeamNotArrived(player));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
