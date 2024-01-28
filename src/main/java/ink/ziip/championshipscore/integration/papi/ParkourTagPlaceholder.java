package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ParkourTagPlaceholder extends BasePlaceholder {
    public ParkourTagPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "parkourtag";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        ParkourTagManager parkourTagManager = plugin.getGameManager().getParkourTagManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("area_status_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }
            return parkourTagArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_team_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("area_team_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }
            return parkourTagArea.getLeftChampionshipTeam().getColoredName();
        }
        if (params.startsWith("area_rival_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("area_rival_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }
            return parkourTagArea.getRightChampionshipTeam().getColoredName();
        }
        if (params.startsWith("area_timer_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("area_timer_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }
            return String.valueOf(parkourTagArea.getTimer() + 1);
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return null;
        Location location = player.getLocation();

        if (params.startsWith("area_chaser_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("area_chaser_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null)
                return "None";

            return championshipTeam.getColoredColor() + player.getName();
        }

        if (params.startsWith("area_escapees_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("area_escapees_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }

            return String.valueOf(parkourTagArea.getAreaEscapeesNums(location));
        }

        if (params.startsWith("area_survived_players_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("area_survived_players_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }

            return String.valueOf(parkourTagArea.getAreaSurvivedEscapeesNums(location));
        }

        if (params.startsWith("player_role_")) {
            ParkourTagArea parkourTagArea = parkourTagManager.getArea(params.replace("player_role_", ""));
            if (parkourTagArea == null) {
                parkourTagArea = parkourTagManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (parkourTagArea == null) {
                return null;
            }
            if (parkourTagArea.notAreaPlayer(player))
                return "Spectator";

            if (parkourTagArea.isChaser(player))
                return "Chaser";
            else
                return "Escapee";
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}
