package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.state.TeamBuildState;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BuildMartPlaceholder extends BaseGamePlaceholder<BuildMartArea> {
    public BuildMartPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "buildmart";
    }

    @Override
    protected BaseGameInstanceManager<BuildMartArea> getManager() {
        return plugin.getGameManager().getBuildMartManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {
        if (params.startsWith("area_golden_blueprint_")) {
            BuildMartArea area = resolveArea(params, "area_golden_blueprint_", offlinePlayer);
            BuildMartBlueprint blueprint = area == null ? null : area.getCurrentGolden();
            return blueprint == null ? MessageConfig.PLACEHOLDER_NONE : blueprint.getDisplayName();
        }
        if (params.startsWith("area_golden_countdown_")) {
            BuildMartArea area = resolveArea(params, "area_golden_countdown_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE : String.valueOf(area.goldenSecondsRemaining());
        }
        if (params.startsWith("player_completed_builds_")) {
            TeamBuildState state = resolvePlayerTeamState(params, "player_completed_builds_", offlinePlayer);
            return state == null ? MessageConfig.PLACEHOLDER_NONE : String.valueOf(state.getCompletedCount());
        }
        if (params.startsWith("player_total_stars_")) {
            TeamBuildState state = resolvePlayerTeamState(params, "player_total_stars_", offlinePlayer);
            return state == null ? MessageConfig.PLACEHOLDER_NONE : String.valueOf(state.getTotalStars());
        }
        return null;
    }

    private TeamBuildState resolvePlayerTeamState(String params, String prefix, OfflinePlayer offlinePlayer) {
        BuildMartArea area = resolveArea(params, prefix, offlinePlayer);
        Player player = offlinePlayer.getPlayer();
        if (area == null || player == null) return null;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        return team == null ? null : area.teamStateOf(team);
    }
}
