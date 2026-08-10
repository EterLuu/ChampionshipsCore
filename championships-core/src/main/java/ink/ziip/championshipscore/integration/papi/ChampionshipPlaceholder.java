package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.daily.DailyPlayerSnapshot;
import ink.ziip.championshipscore.api.daily.DailyStatSnapshot;
import ink.ziip.championshipscore.api.daily.DailyLeaderboardEntry;
import ink.ziip.championshipscore.api.daily.DailyLeaderboardMenu;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
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
        if (params == null) return null;
        if (params.startsWith("daily_")) return daily(offlinePlayer, params.substring("daily_".length()));
        if (offlinePlayer == null) return MessageConfig.PLACEHOLDER_NONE;
        if (params.startsWith("player_team_name_no_color")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_SPECTATOR;

            return championshipTeam.getName();
        }
        if (params.startsWith("player_team_name")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_SPECTATOR;

            return championshipTeam.getColoredName();
        }
        if (params.startsWith("player_team_color_code")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_NONE;

            return championshipTeam.getColorCode();
        }
        if (params.startsWith("player_team_color")) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_NONE;

            return championshipTeam.getColorName();
        }

        if (params.startsWith("player_points")) {
            return Utils.formatPoints(plugin.getRankManager().getPlayerPoints(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_team_points")) {
            return Utils.formatPoints(plugin.getRankManager().getPlayerTeamPoints(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_rank")) {
            return String.valueOf(plugin.getRankManager().getPlayerRank(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_team_rank")) {
            return String.valueOf(plugin.getRankManager().getPlayerTeamRank(offlinePlayer.getUniqueId()));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }

    /** DAILY placeholders only read immutable caches; no DB or mutable queue traversal occurs here. */
    private String daily(OfflinePlayer player, String key) {
        if (plugin.getDailyManager() == null) return safeDailyDefault(key);
        if ("mode".equals(key)) return plugin.getDailyManager().modeDisplay();
        if (key.startsWith("lb_")) return renderLeaderboard(key.substring(3));
        if (player == null) return safeDailyDefault(key);
        try {
            DailyPlayerSnapshot snapshot = plugin.getDailyManager().snapshot(player.getUniqueId());
            return switch (key) {
                case "party_leader" -> snapshot.partyLeader();
                case "party_size" -> Integer.toString(snapshot.partySize());
                case "selected_game" -> snapshot.selectedGame();
                case "queue_state" -> snapshot.queueState();
                case "queue_players" -> Integer.toString(snapshot.queuePlayers());
                case "countdown" -> snapshot.countdown() < 0 ? "-" : Integer.toString(snapshot.countdown());
                case "active_game" -> snapshot.activeGame();
                case "active_map" -> snapshot.activeMap();
                case "match_id" -> snapshot.matchId();
                case "games", "wins", "points", "best" -> renderDailyStat(player, key);
                default -> null;
            };
        } catch (RuntimeException exception) {
            return safeDailyDefault(key);
        }
    }

    private String renderLeaderboard(String request) {
        String field = "row";
        if (request.endsWith("_name")) {
            field = "name";
            request = request.substring(0, request.length() - 5);
        } else if (request.endsWith("_value")) {
            field = "value";
            request = request.substring(0, request.length() - 6);
        }
        int separator = request.lastIndexOf('_');
        if (separator < 1) return MessageConfig.PLACEHOLDER_NONE;
        int rank;
        try { rank = Integer.parseInt(request.substring(separator + 1)); }
        catch (NumberFormatException ignored) { return MessageConfig.PLACEHOLDER_NONE; }
        String board = request.substring(0, separator);
        java.util.List<DailyLeaderboardEntry> entries = plugin.getDailyManager().statsManager().leaderboard(board);
        if (rank < 1 || rank > entries.size()) {
            if ("row".equals(field)) return Utils.translateColorCodes(MessageConfig.DAILY_LEADERBOARD_EMPTY
                    .replace("%rank%", Integer.toString(rank)));
            return MessageConfig.PLACEHOLDER_NONE;
        }
        DailyLeaderboardEntry entry = entries.get(rank - 1);
        String value = entry.duration() ? DailyLeaderboardMenu.formatDuration((long) entry.value())
                : Utils.formatPoints(entry.value());
        if ("name".equals(field)) return entry.name();
        if ("value".equals(field)) return value;
        String template = entry.duration() ? MessageConfig.DAILY_LEADERBOARD_ROW_TIME
                : MessageConfig.DAILY_LEADERBOARD_ROW_POINTS;
        return Utils.translateColorCodes(template.replace("%rank%", Integer.toString(rank))
                .replace("%player%", entry.name()).replace("%value%", value));
    }

    private String renderDailyStat(OfflinePlayer player, String key) {
        DailyStatSnapshot stat = plugin.getDailyManager().statsManager().stat(player.getUniqueId(), null);
        return switch (key) {
            case "games" -> Long.toString(stat.gamesPlayed());
            case "wins" -> Long.toString(stat.wins());
            case "points" -> Utils.formatPoints(stat.totalPoints());
            case "best" -> Utils.formatPoints(stat.bestPoints());
            default -> MessageConfig.PLACEHOLDER_NONE;
        };
    }

    private String safeDailyDefault(String key) {
        return switch (key) {
            case "party_size", "queue_players", "games", "wins", "points", "best" -> "0";
            case "mode" -> MessageConfig.DAILY_MODE_CHAMPIONSHIP;
            default -> MessageConfig.PLACEHOLDER_NONE;
        };
    }
}
