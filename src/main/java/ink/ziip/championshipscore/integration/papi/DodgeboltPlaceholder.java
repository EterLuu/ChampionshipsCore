package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltSide;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class DodgeboltPlaceholder extends BaseGamePlaceholder<DodgeboltArea> {
    public DodgeboltPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dodgebolt";
    }

    @Override
    protected BaseGameInstanceManager<DodgeboltArea> getManager() {
        return plugin.getGameManager().getDodgeboltManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {
        if (params.startsWith("area_round_")) {
            DodgeboltArea area = resolveArea(params, "area_round_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE : String.valueOf(area.getRoundNumber());
        }
        if (params.startsWith("area_shots_")) {
            DodgeboltArea area = resolveArea(params, "area_shots_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE : String.valueOf(area.getShotsThisRound());
        }
        for (DodgeboltSide side : DodgeboltSide.values()) {
            String sideName = side.name().toLowerCase(java.util.Locale.ROOT);
            String teamPrefix = "area_" + sideName + "_team_";
            if (params.startsWith(teamPrefix)) {
                DodgeboltArea area = resolveArea(params, teamPrefix, offlinePlayer);
                ChampionshipTeam team = area == null ? null : teamOf(area, side);
                return team == null ? MessageConfig.PLACEHOLDER_NONE : team.getColoredName();
            }
            String winsPrefix = "area_" + sideName + "_wins_";
            if (params.startsWith(winsPrefix)) {
                DodgeboltArea area = resolveArea(params, winsPrefix, offlinePlayer);
                if (area == null) return MessageConfig.PLACEHOLDER_NONE;
                return String.valueOf(side == DodgeboltSide.RIGHT ? area.getRightWins() : area.getLeftWins());
            }
            String alivePrefix = "area_" + sideName + "_alive_";
            if (params.startsWith(alivePrefix)) {
                DodgeboltArea area = resolveArea(params, alivePrefix, offlinePlayer);
                return area == null ? MessageConfig.PLACEHOLDER_NONE : String.valueOf(area.getAliveCount(side));
            }
        }
        return null;
    }

    private ChampionshipTeam teamOf(DodgeboltArea area, DodgeboltSide side) {
        return side == DodgeboltSide.RIGHT ? area.getRightChampionshipTeam() : area.getLeftChampionshipTeam();
    }
}
