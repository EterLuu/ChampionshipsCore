package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.platform.bukkit.text.ChampionshipTabText;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

/** Pure resolver kept separate from PlaceholderAPI so manifest semantics remain unit-testable. */
final class WorkerChampionshipPlaceholderValues {
    private WorkerChampionshipPlaceholderValues() {
    }

    static String resolve(PlayerSnapshot player, TeamSnapshot team, BingoPresentation presentation,
                          String params) {
        return resolve(player, team, presentation, params, false);
    }

    static String resolve(PlayerSnapshot player, TeamSnapshot team, BingoPresentation presentation,
                          String params, boolean daily) {
        String none = presentation.message("papi.none");
        String spectator = presentation.message("papi.spectator");
        if (params.equals("tab_prefix")) {
            if (player != null && player.role() == ParticipantRole.PLAYER) {
                return ChampionshipTabText.gamePrefix(
                        presentation.message("game.name"));
            }
            String name = team == null
                    ? daily && player != null && player.role() == ParticipantRole.SPECTATOR
                    ? presentation.message("presentation.daily-game")
                            .replace("%game%", presentation.message("game.name")) : spectator
                    : LegacyText.translateColorCodes(team.colorCode() + team.name());
            return ChampionshipTabText.bracketedPrefix(name);
        }
        if (params.equals("tab_name_color")) {
            boolean activePlayer = player != null && player.role() == ParticipantRole.PLAYER && team != null;
            return ChampionshipTabText.playerNameColor(team == null ? null : team.colorCode(), activePlayer);
        }
        if (params.equals("tab_footer_status")) {
            if (daily && team != null)
                return ChampionshipTabText.dailyTeamFooter(
                        presentation.message("presentation.tab.daily-team-footer"),
                        LegacyText.translateColorCodes(team.colorCode() + team.name()));
            if (daily)
                return ChampionshipTabText.currentGameFooter(
                        presentation.message("presentation.tab.current-game-footer"),
                        presentation.message("game.name"));
            String name = team == null ? spectator : LegacyText.translateColorCodes(team.colorCode() + team.name());
            return ChampionshipTabText.teamFooter(
                    presentation.message("presentation.tab.team-footer"), name,
                    team == null ? 0D : team.points());
        }

        if (params.startsWith("player_team_name_no_color")) {
            return team == null ? spectator : team.name();
        }
        if (params.startsWith("player_team_name")) {
            return team == null ? spectator : LegacyText.translateColorCodes(team.colorCode() + team.name());
        }
        if (params.startsWith("player_team_color_code")) {
            return team == null ? none : team.colorCode();
        }
        if (params.startsWith("player_team_color")) {
            return team == null ? none : team.colorName();
        }
        if (params.startsWith("player_points")) {
            return LegacyText.formatPoints(player == null ? 0D : player.points());
        }
        if (params.startsWith("player_team_points")) {
            if (daily) return "0";
            return LegacyText.formatPoints(team == null ? 0D : team.points());
        }
        return null;
    }
}
