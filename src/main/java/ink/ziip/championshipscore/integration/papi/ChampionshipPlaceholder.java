package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class ChampionshipPlaceholder extends BasePlaceholder {
    public ChampionshipPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cc";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params.startsWith("player_team_name")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return null;

            return championshipTeam.getColoredName();
        }
        if (params.startsWith("player_team_color")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return null;

            return championshipTeam.getColorName();
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
