package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.daily.DailyPlayerSnapshot;
import ink.ziip.championshipscore.api.daily.DailyStatSnapshot;
import ink.ziip.championshipscore.api.daily.DailyLeaderboardEntry;
import ink.ziip.championshipscore.api.daily.DailyLeaderboardMenu;
import ink.ziip.championshipscore.api.daily.DailyMetric;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.ChampionshipTabText;
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
        if (params.equals("tab_prefix")) return tabPrefix(offlinePlayer);
        if (params.equals("tab_name_color")) return tabNameColor(offlinePlayer);
        if (params.equals("tab_footer_status")) return tabFooterStatus(offlinePlayer);
        if (params.startsWith("player_team_name_no_color")) {
            ChampionshipTeam championshipTeam = visibleTeam(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_SPECTATOR;

            return championshipTeam.getName();
        }
        if (params.startsWith("player_team_name")) {
            ChampionshipTeam championshipTeam = visibleTeam(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_SPECTATOR;

            return championshipTeam.getColoredName();
        }
        if (params.startsWith("player_team_color_code")) {
            ChampionshipTeam championshipTeam = visibleTeam(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_NONE;

            return championshipTeam.getColorCode();
        }
        if (params.startsWith("player_team_color")) {
            ChampionshipTeam championshipTeam = visibleTeam(offlinePlayer);
            if (championshipTeam == null)
                return MessageConfig.PLACEHOLDER_NONE;

            return championshipTeam.getColorName();
        }

        if (params.startsWith("player_points")) {
            return Utils.formatPoints(plugin.getRankManager().getPlayerPoints(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_team_points")) {
            if (isDaily()) return "0";
            return Utils.formatPoints(plugin.getRankManager().getPlayerTeamPoints(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_rank")) {
            return String.valueOf(plugin.getRankManager().getPlayerRank(offlinePlayer.getUniqueId()));
        }
        if (params.startsWith("player_team_rank")) {
            if (isDaily()) return "0";
            return String.valueOf(plugin.getRankManager().getPlayerTeamRank(offlinePlayer.getUniqueId()));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }

    private boolean isDaily() {
        return plugin.getDailyManager() != null && plugin.getDailyManager().isDailyLobby();
    }

    private String tabPrefix(OfflinePlayer player) {
        BaseGameInstance activeGame = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        if (activeGame != null) {
            return ChampionshipTabText.gamePrefix(activeGame.getGameTypeEnum().toString());
        }
        if (isDaily()) {
            ChampionshipTeam team = visibleTeam(player);
            if (team != null) return ChampionshipTabText.bracketedPrefix(team.getColoredName());
            BaseGameInstance area = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
            if (area == null) area = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            String status = area == null ? MessageConfig.PRESENTATION_DAILY_LOBBY
                    : MessageConfig.PRESENTATION_DAILY_GAME.replace("%game%", area.getGameTypeEnum().toString());
            return ChampionshipTabText.bracketedPrefix(status);
        }
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        String name = team == null ? MessageConfig.PLACEHOLDER_SPECTATOR : team.getColoredName();
        return ChampionshipTabText.bracketedPrefix(name);
    }

    private String tabNameColor(OfflinePlayer player) {
        BaseGameInstance area = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        ChampionshipTeam team = visibleTeam(player);
        return ChampionshipTabText.playerNameColor(team == null ? null : team.getColorCode(),
                area != null && team != null);
    }

    private String tabFooterStatus(OfflinePlayer player) {
        if (isDaily()) {
            ChampionshipTeam team = visibleTeam(player);
            if (team != null) return ChampionshipTabText.dailyTeamFooter(
                    MessageConfig.PRESENTATION_TAB_DAILY_TEAM_FOOTER, team.getColoredName());
            DailyPlayerSnapshot snapshot = plugin.getDailyManager().snapshot(player.getUniqueId());
            String game = "-".equals(snapshot.activeGame()) ? snapshot.selectedGame() : snapshot.activeGame();
            return ChampionshipTabText.currentGameFooter(
                    MessageConfig.PRESENTATION_TAB_CURRENT_GAME_FOOTER, game);
        }
        ChampionshipTeam team = visibleTeam(player);
        String name = team == null ? MessageConfig.PLACEHOLDER_SPECTATOR : team.getColoredName();
        return ChampionshipTabText.teamFooter(MessageConfig.PRESENTATION_TAB_TEAM_FOOTER, name,
                plugin.getRankManager().getPlayerTeamPoints(player.getUniqueId()));
    }

    /** DAILY must never fall through to the player's persistent championship identity. */
    private ChampionshipTeam visibleTeam(OfflinePlayer player) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        return isDaily() && !plugin.getTeamManager().isTransientTeam(team) ? null : team;
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
                case "games", "points", "best" -> renderDailyStat(player, key);
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
        String value = formatBoardValue(board, entry);
        if ("name".equals(field)) return entry.name();
        if ("value".equals(field)) return value;
        String template = entry.duration() ? MessageConfig.DAILY_LEADERBOARD_ROW_TIME
                : MessageConfig.DAILY_LEADERBOARD_ROW_COUNT;
        return Utils.translateColorCodes(template.replace("%rank%", Integer.toString(rank))
                .replace("%player%", entry.name()).replace("%value%", value));
    }

    /** Unified-metric boards format by metric kind (damage/percent/count); legacy boards by duration flag. */
    private String formatBoardValue(String board, DailyLeaderboardEntry entry) {
        String normalized = board.toLowerCase(java.util.Locale.ROOT);
        for (DailyMetric metric : DailyMetric.values()) {
            String prefix = metric.name().toLowerCase(java.util.Locale.ROOT) + "_";
            if (normalized.equals(metric.boardId(null)) || normalized.startsWith(prefix + "map_")) {
                return plugin.getDailyManager().statsManager().formatLeaderboardValue(metric, entry);
            }
        }
        return entry.duration() ? DailyLeaderboardMenu.formatDuration((long) entry.value())
                : Long.toString(Math.round(entry.value()));
    }

    private String renderDailyStat(OfflinePlayer player, String key) {
        DailyStatSnapshot stat = plugin.getDailyManager().statsManager().stat(player.getUniqueId(), null);
        return switch (key) {
            case "games" -> Long.toString(stat.gamesPlayed());
            // Retained as zero-only compatibility placeholders. DAILY points are match-local.
            case "points", "best" -> "0";
            default -> MessageConfig.PLACEHOLDER_NONE;
        };
    }

    private String safeDailyDefault(String key) {
        return switch (key) {
            case "party_size", "queue_players", "games", "points", "best" -> "0";
            case "mode" -> MessageConfig.DAILY_MODE_CHAMPIONSHIP;
            default -> MessageConfig.PLACEHOLDER_NONE;
        };
    }
}
