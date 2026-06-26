package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ParkourTagPlaceholder extends BaseGamePlaceholder<ParkourTagArea> {
    public ParkourTagPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "parkourtag";
    }

    @Override
    protected BaseAreaManager<ParkourTagArea> getManager() {
        return plugin.getGameManager().getParkourTagManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Non-Player required placeholders */

        if (params.startsWith("area_team_")) {
            ParkourTagArea parkourTagArea = resolveArea(params, "area_team_", offlinePlayer);
            if (parkourTagArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = parkourTagArea.getLeftChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getName();
        }
        if (params.startsWith("area_rival_")) {
            ParkourTagArea parkourTagArea = resolveArea(params, "area_rival_", offlinePlayer);
            if (parkourTagArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = parkourTagArea.getRightChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getName();
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;
        Location location = player.getLocation();

        if (params.startsWith("area_chaser_")) {
            ParkourTagArea parkourTagArea = resolveArea(params, "area_chaser_", offlinePlayer);
            if (parkourTagArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_NONE;

            return championshipTeam.getColoredColor() + player.getName();
        }

        if (params.startsWith("area_escapees_")) {
            ParkourTagArea parkourTagArea = resolveArea(params, "area_escapees_", offlinePlayer);
            if (parkourTagArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }

            return String.valueOf(parkourTagArea.getAreaEscapeesNums(location));
        }

        if (params.startsWith("area_survived_players_")) {
            ParkourTagArea parkourTagArea = resolveArea(params, "area_survived_players_", offlinePlayer);
            if (parkourTagArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }

            return String.valueOf(parkourTagArea.getAreaSurvivedEscapeesNums(location));
        }

        if (params.startsWith("player_role_")) {
            ParkourTagArea parkourTagArea = resolveArea(params, "player_role_", offlinePlayer);
            if (parkourTagArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            if (parkourTagArea.notAreaPlayer(player))
                return MessageConfig.PLACEHOLDER_PARKOUR_TAG_SPECTATOR;

            if (parkourTagArea.isChaser(player))
                return MessageConfig.PLACEHOLDER_PARKOUR_TAG_CHASER;
            else
                return MessageConfig.PLACEHOLDER_PARKOUR_TAG_ESCAPEE;
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
