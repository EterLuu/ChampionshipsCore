package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AceRacePlaceholder extends BaseGamePlaceholder<AceRaceArea> {
    public AceRacePlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "acerace";
    }

    @Override
    protected BaseGameInstanceManager<AceRaceArea> getManager() {
        return plugin.getGameManager().getAceRaceManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {
        if (params == null) return null;
        if (params.startsWith("area_finished_players_")) {
            AceRaceArea area = resolveArea(params, "area_finished_players_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE : String.valueOf(area.getFinishedPlayers().size());
        }
        if (params.startsWith("player_position_")) {
            AceRaceArea area = resolvePlayerArea(params, "player_position_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE
                    : area.getPlayerPositionDisplay(offlinePlayer.getUniqueId())
                    + "/" + area.getGamePlayers().size();
        }
        if (params.startsWith("player_lap_")) {
            AceRaceArea area = resolvePlayerArea(params, "player_lap_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE
                    : area.getCurrentLap(offlinePlayer.getUniqueId()) + "/" + area.getGameConfig().getLaps();
        }
        String progressPrefix = params.startsWith("player_progress_point_")
                ? "player_progress_point_" : params.startsWith("player_checkpoint_")
                ? "player_checkpoint_" : null;
        if (progressPrefix != null) {
            AceRaceArea area = resolvePlayerArea(params, progressPrefix, offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE
                    : area.getReachedProgressPoint(offlinePlayer.getUniqueId()) + "/" + area.getProgressPoints().size();
        }
        return null;
    }

    private AceRaceArea resolvePlayerArea(String params, String prefix, OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) return null;
        AceRaceArea area = resolveArea(params, prefix, offlinePlayer);
        Player player = offlinePlayer.getPlayer();
        return area != null && player != null && area.getGamePlayers().contains(player.getUniqueId()) ? area : null;
    }
}
