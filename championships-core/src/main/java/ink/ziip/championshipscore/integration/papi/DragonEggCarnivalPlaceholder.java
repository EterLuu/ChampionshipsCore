package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class DragonEggCarnivalPlaceholder extends BaseGamePlaceholder<DragonEggCarnivalArea> {
    public DragonEggCarnivalPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "decarnival";
    }

    @Override
    protected BaseGameInstanceManager<DragonEggCarnivalArea> getManager() {
        return plugin.getGameManager().getDragonEggCarnivalManager();
    }

    @Override
    protected String areaTimer(DragonEggCarnivalArea area) {
        if (area.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return String.valueOf(0);
        }
        return String.valueOf(area.getTimer());
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_team_wins_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_team_wins_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam team = displayedTeam(dragonEggCarnivalArea, offlinePlayer, false);
            return String.valueOf(pointsOf(dragonEggCarnivalArea, team));
        }
        if (params.startsWith("area_team_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_team_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = displayedTeam(dragonEggCarnivalArea, offlinePlayer, false);
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getColoredName();
        }
        if (params.startsWith("area_rival_wins_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_rival_wins_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam team = displayedTeam(dragonEggCarnivalArea, offlinePlayer, true);
            return String.valueOf(pointsOf(dragonEggCarnivalArea, team));
        }
        if (params.startsWith("area_rival_")) {
            DragonEggCarnivalArea dragonEggCarnivalArea = resolveArea(params, "area_rival_", offlinePlayer);
            if (dragonEggCarnivalArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = displayedTeam(dragonEggCarnivalArea, offlinePlayer, true);
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getColoredName();
        }
        // Placeholder is unknown by the Expansion
        return null;
    }

    private ChampionshipTeam displayedTeam(DragonEggCarnivalArea area, OfflinePlayer viewer, boolean rival) {
        ChampionshipTeam own = viewer == null ? null : plugin.getTeamManager().getTeamByPlayer(viewer);
        if (own != null && (own.equals(area.getRightChampionshipTeam()) || own.equals(area.getLeftChampionshipTeam()))) {
            if (!rival) return own;
            return own.equals(area.getRightChampionshipTeam())
                    ? area.getLeftChampionshipTeam() : area.getRightChampionshipTeam();
        }
        return rival ? area.getRightChampionshipTeam() : area.getLeftChampionshipTeam();
    }

    private static int pointsOf(DragonEggCarnivalArea area, ChampionshipTeam team) {
        if (team == null) return 0;
        return team.equals(area.getRightChampionshipTeam()) ? area.getRightTeamPoints() : area.getLeftTeamPoints();
    }
}
