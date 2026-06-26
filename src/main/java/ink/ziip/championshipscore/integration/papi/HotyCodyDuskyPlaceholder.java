package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class HotyCodyDuskyPlaceholder extends BaseGamePlaceholder<HotyCodyDuskyTeamArea> {
    public HotyCodyDuskyPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hotycodydusky";
    }

    @Override
    protected BaseAreaManager<HotyCodyDuskyTeamArea> getManager() {
        return plugin.getGameManager().getHotyCodyDuskyManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_survived_players_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = resolveArea(params, "area_survived_players_", offlinePlayer);
            if (hotyCodyDuskyTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(hotyCodyDuskyTeamArea.getSurvivedPlayerNums());
        }
        if (params.startsWith("area_survived_teams_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = resolveArea(params, "area_survived_teams_", offlinePlayer);
            if (hotyCodyDuskyTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(hotyCodyDuskyTeamArea.getSurvivedTeamNums());
        }
        if (params.startsWith("area_cody_holder_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = resolveArea(params, "area_cody_holder_", offlinePlayer);
            if (hotyCodyDuskyTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (hotyCodyDuskyTeamArea.getCodyHolder() == null)
                return MessageConfig.PLACEHOLDER_NONE;
            String name = ChampionshipsCore.getInstance().getPlayerManager().getPlayerName(hotyCodyDuskyTeamArea.getCodyHolder());
            if (name != null)
                return name;
            else
                return MessageConfig.PLACEHOLDER_NONE;
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
