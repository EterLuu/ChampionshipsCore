package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.advancementcc.AdvancementCCArea;
import ink.ziip.championshipscore.api.game.advancementcc.AdvancementCCManager;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdvancementCCPlaceholder extends BasePlaceholder {
    public AdvancementCCPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "acc";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        AdvancementCCManager advancementCCManager = plugin.getGameManager().getAdvancementCCManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            AdvancementCCArea advancementCCArea = advancementCCManager.getArea(params.replace("area_status_", ""));
            if (advancementCCArea == null) {
                advancementCCArea = advancementCCManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (advancementCCArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return advancementCCArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            AdvancementCCArea
                    advancementCCArea = advancementCCManager.getArea(params.replace("area_timer_", ""));
            if (advancementCCArea == null) {
                advancementCCArea = advancementCCManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (advancementCCArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(advancementCCArea.getTimer() + 1);
        }
        if (params.startsWith("area_scores_")) {
            AdvancementCCArea
                    advancementCCArea = advancementCCManager.getArea(params.replace("area_scores_", ""));
            if (advancementCCArea == null) {
                advancementCCArea = advancementCCManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (advancementCCArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            try {
                return String.valueOf(advancementCCArea.getTeamPoints(advancementCCArea.getGameTeams().getFirst()));
            } catch (Exception ignored) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
        }
        if (params.startsWith("is_advancement_completed_")) {
            AdvancementCCArea advancementCCArea = advancementCCManager.getArea(params.split("_", 5)[3]);
            if (advancementCCArea == null) {
                advancementCCArea = advancementCCManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (advancementCCArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            String advancementName = params.split("_", 5)[4];
            return String.valueOf(advancementCCArea.isAdvancementCompleted(advancementName));
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        // Placeholder is unknown by the Expansion
        return null;
    }
}
