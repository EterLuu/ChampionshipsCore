package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

/** Pure resolver kept separate from PlaceholderAPI so manifest semantics remain unit-testable. */
final class WorkerChampionshipPlaceholderValues {
    private WorkerChampionshipPlaceholderValues() {
    }

    static String resolve(PlayerSnapshot player, TeamSnapshot team, BingoPresentation presentation,
                          String params) {
        String none = presentation.messages().getOrDefault("papi.none", "无");
        String spectator = presentation.messages().getOrDefault("papi.spectator", "旁观");

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
            return LegacyText.formatPoints(team == null ? 0D : team.points());
        }
        return null;
    }
}
