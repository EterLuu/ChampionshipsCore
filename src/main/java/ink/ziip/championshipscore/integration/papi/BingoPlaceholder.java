package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.BingoArea;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PlaceholderAPI expansion for the Bingo game (identifier {@code bingo}).
 *
 * <p>Exposes the shared {@code area_status_}/{@code area_timer_} placeholders inherited from
 * {@link BaseGamePlaceholder}, plus the bingo-specific placeholders used by the scoreboard:
 * <ul>
 *   <li>{@code %bingo_current_time%} / {@code %bingo_current_time_[areaName]%} - remaining countdown.</li>
 *   <li>{@code %bingo_current_tasks_team%} / {@code %bingo_current_tasks_team_[areaName]%} - the
 *       requesting player's team completed-task count.</li>
 *   <li>{@code %bingo_area_rank_1_[areaName]%} .. {@code %bingo_area_rank_4_[areaName]%} - the top
 *       four teams by score, one per line, formatted {@code "name: score"}.</li>
 * </ul>
 * The {@code current_*} placeholders omit the area name and resolve to the requesting player's
 * current area; the {@code area_*} placeholders take an explicit area name with the same fallback.
 */
public class BingoPlaceholder extends BaseGamePlaceholder<BingoArea> {
    public BingoPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bingo";
    }

    @Override
    protected BaseGameInstanceManager<BingoArea> getManager() {
        return plugin.getGameManager().getBingoManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Countdown timer: %bingo_current_time% or %bingo_current_time_[areaName]% */
        if (params.equals("current_time") || params.startsWith("current_time_")) {
            String areaName = params.equals("current_time") ? "" : params.substring("current_time_".length());
            BingoArea area = resolveAreaByName(areaName, offlinePlayer);
            if (area == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return areaTimer(area);
        }

        /* Requesting player's team completed-task count */
        if (params.equals("current_tasks_team") || params.startsWith("current_tasks_team_")) {
            String areaName = params.equals("current_tasks_team") ? "" : params.substring("current_tasks_team_".length());
            BingoArea area = resolveAreaByName(areaName, offlinePlayer);
            Player player = offlinePlayer.getPlayer();
            if (area == null || player == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            BingoRound round = area.getRound();
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
            if (round == null || team == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(round.completedCount(team));
        }

        /* Leaderboard: top four teams by score, one team per placeholder line. */
        if (params.startsWith("area_rank_1_")) {
            return rankEntry(resolveArea(params, "area_rank_1_", offlinePlayer), 0);
        }
        if (params.startsWith("area_rank_2_")) {
            return rankEntry(resolveArea(params, "area_rank_2_", offlinePlayer), 1);
        }
        if (params.startsWith("area_rank_3_")) {
            return rankEntry(resolveArea(params, "area_rank_3_", offlinePlayer), 2);
        }
        if (params.startsWith("area_rank_4_")) {
            return rankEntry(resolveArea(params, "area_rank_4_", offlinePlayer), 3);
        }

        // Placeholder is unknown by the Expansion
        return null;
    }

    /** Single leaderboard entry: {@code "name: score"} for the team at {@code index} (0-based). */
    private String rankEntry(BingoArea area, int index) {
        if (area == null) {
            return MessageConfig.PLACEHOLDER_NONE;
        }
        BingoRound round = area.getRound();
        if (round == null) {
            return MessageConfig.PLACEHOLDER_NONE;
        }
        List<ChampionshipTeam> ranked = round.rankedTeams();
        if (index >= ranked.size()) {
            return MessageConfig.PLACEHOLDER_NONE;
        }
        ChampionshipTeam team = ranked.get(index);
        return team.getName() + ": " + round.score(team);
    }
}
