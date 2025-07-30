package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class HotyCodyDuskyPlaceholder extends BasePlaceholder {
    public HotyCodyDuskyPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "hotycodydusky";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        HotyCodyDuskyManager hotyCodyDuskyManager = plugin.getGameManager().getHotyCodyDuskyManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(params.replace("area_status_", ""));
            if (hotyCodyDuskyTeamArea == null) {
                hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (hotyCodyDuskyTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return hotyCodyDuskyTeamArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(params.replace("area_timer_", ""));
            if (hotyCodyDuskyTeamArea == null) {
                hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (hotyCodyDuskyTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(hotyCodyDuskyTeamArea.getTimer() + 1);
        }
        if (params.startsWith("area_survived_players_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(params.replace("area_survived_players_", ""));
            if (hotyCodyDuskyTeamArea == null) {
                hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (hotyCodyDuskyTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(hotyCodyDuskyTeamArea.getSurvivedPlayerNums());
        }
        if (params.startsWith("area_survived_teams_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(params.replace("area_survived_teams_", ""));
            if (hotyCodyDuskyTeamArea == null) {
                hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (hotyCodyDuskyTeamArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(hotyCodyDuskyTeamArea.getSurvivedTeamNums());
        }
        if (params.startsWith("area_cody_holder_")) {
            HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(params.replace("area_cody_holder_", ""));
            if (hotyCodyDuskyTeamArea == null) {
                hotyCodyDuskyTeamArea = hotyCodyDuskyManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
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
