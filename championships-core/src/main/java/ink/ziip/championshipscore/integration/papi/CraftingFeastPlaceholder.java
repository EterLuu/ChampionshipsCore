package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.craftingfeast.runtime.CraftingFeastArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.OfflinePlayer;

public final class CraftingFeastPlaceholder extends BaseGamePlaceholder<CraftingFeastArea> {
    public CraftingFeastPlaceholder(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public String getIdentifier() {
        return "craftingfeast";
    }

    @Override
    protected BaseGameInstanceManager<CraftingFeastArea> getManager() {
        return plugin.getGameManager().getCraftingFeastManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer player, String params) {
        if (player == null) return null;
        for (String metric :
                new String[] {"score", "completed", "missed", "delivered", "rating", "streak"}) {
            String prefix = "player_" + metric + "_";
            if (!params.startsWith(prefix)) continue;
            CraftingFeastArea area = resolveArea(params, prefix, player);
            if (area == null) return "rating".equals(metric) ? "" : "0";
            return switch (metric) {
                case "score" -> LegacyText.formatPoints(area.score(player.getUniqueId()));
                case "completed" -> Integer.toString(area.completed(player.getUniqueId()));
                case "missed" -> Integer.toString(area.missed(player.getUniqueId()));
                case "delivered" -> Integer.toString(area.delivered(player.getUniqueId()));
                case "rating" -> LegacyText.translateColorCodes(area.rating(player.getUniqueId()));
                default -> Integer.toString(area.streak(player.getUniqueId()));
            };
        }
        return null;
    }
}
