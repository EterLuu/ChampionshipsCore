package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.integration.bingo.BingoManager;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class BingoPlaceholder extends BasePlaceholder {
    public BingoPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bingo";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        BingoManager bingoManager = plugin.getBingoManager();

        /* Non-Player required placeholders */

        if (params.startsWith("bingo_team_points_")) {
            Material material = Material.getMaterial(params.replace("bingo_team_points_", ""));
            if (material == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }

            return String.valueOf(bingoManager.getMaterialPoints(material));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
