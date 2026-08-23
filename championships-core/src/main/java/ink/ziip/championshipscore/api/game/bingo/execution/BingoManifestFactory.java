package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyManager;
import ink.ziip.championshipscore.api.game.bingo.BingoConfig;
import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.BingoRuntimeRules;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.BingoManifestHasher;
import ink.ziip.championshipscore.protocol.BingoDimension;
import ink.ziip.championshipscore.protocol.BingoIntroductionMode;
import ink.ziip.championshipscore.protocol.BingoLocationSnapshot;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchRunMode;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.ProtocolVersion;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Freezes Core's mutable roster/config/card state into one immutable remote-match manifest. */
public final class BingoManifestFactory {
    private final ChampionshipsCore plugin;

    public BingoManifestFactory(ChampionshipsCore plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    public MatchManifest create(UUID matchId, long epoch, String workerId, BingoConfig config,
                                GameRunMode runMode, List<ChampionshipTeam> teams,
                                Set<UUID> spectators, boolean showIntroduction,
                                BingoVariantRules variant) {
        long cardSeed = java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        if (runMode != GameRunMode.DAILY) variant = BingoVariantRules.FIXED_POINTS;
        CardSize cardSize = variant.remix() == BingoRemix.SPEEDRUN ? CardSize.X3
                : variant.remix() == BingoRemix.SCALE
                ? (java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? CardSize.X3 : CardSize.X4)
                : CardSize.fromWidth(config.getCardWidth());
        Set<TaskData.TaskType> types = Set.of(TaskData.TaskType.ITEM, TaskData.TaskType.ITEM_SET,
                TaskData.TaskType.ADVANCEMENT, TaskData.TaskType.STATISTIC, TaskData.TaskType.EVENT);
        if (variant.remix() == BingoRemix.FEAST) {
            types = java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                    ? Set.of(TaskData.TaskType.ADVANCEMENT)
                    : Set.of(TaskData.TaskType.STATISTIC, TaskData.TaskType.EVENT);
        }
        Set<String> excludes = new java.util.HashSet<>();
        Map<String, Integer> caps = new java.util.HashMap<>(Map.of("kill", 2));
        if (variant.difficulty().maxEndTasks() == 0) excludes.add("dim:the_end");
        else if (variant.difficulty().maxEndTasks() > 0)
            caps.put("dim:the_end", variant.difficulty().maxEndTasks());
        if (variant.remix() == BingoRemix.UPGRADE) excludes.add("dim:the_end");
        BingoRound generated = new BingoRound(cardSize, cardSeed, types, excludes, caps, teams,
                config.pointsArray(), config.getLineBonus(), config.getLineBonusMajorCount(),
                config.getLineBonusMinor(), variant);
        List<BingoTaskSpec> tasks = BingoTaskSpecMapper.toSpecs(generated.layout());
        BingoScoringRules scoring = new BingoScoringRules(cardSize.size, config.getPointsPerRank(),
                config.getLineBonus(), config.getLineBonusMajorCount(), config.getLineBonusMinor(), variant);
        org.bukkit.Location spectatorSpawn = config.getSpectatorSpawnPoint();
        org.bukkit.Location introductionSpawn = config.getIntroductionSpawnPoint() != null
                ? config.getIntroductionSpawnPoint() : spectatorSpawn;
        boolean daily = runMode == GameRunMode.DAILY;
        BingoRuntimeRules runtimeRules = new BingoRuntimeRules(config.getPrepareTime(), 5,
                daily ? 180 : config.getScatterRadius(), daily ? 32 : 0,
                daily ? 40 : config.getScatterMaxTries(), 180,
                daily ? List.of() : config.getPermanentEffects(), showIntroduction, 45,
                config.getRules() == null ? List.of() : config.getRules(),
                config.getIntroductionGameMode() == org.bukkit.GameMode.SPECTATOR
                        ? BingoIntroductionMode.SPECTATOR : BingoIntroductionMode.ADVENTURE,
                location(introductionSpawn), location(spectatorSpawn), presentation(runMode));

        List<TeamSnapshot> teamSnapshots = new ArrayList<>();
        List<PlayerSnapshot> participants = new ArrayList<>();
        for (int teamIndex = 0; teamIndex < teams.size(); teamIndex++) {
            ChampionshipTeam team = teams.get(teamIndex);
            // Runtime DAILY teams intentionally use negative Core-only IDs. The worker protocol uses
            // a compact match-local namespace so those teams never need a persisted database ID.
            int protocolTeamId = team.getId() < 0 ? teamIndex : team.getId();
            List<UUID> members = team.getMembers().stream().sorted().toList();
            double teamPoints = runMode == GameRunMode.EVENT ? plugin.getRankManager().getTeamPoints(team) : 0D;
            String displayName = runMode == GameRunMode.DAILY
                    ? DailyManager.teamNameForColor(team.getColorName()) : team.getName();
            teamSnapshots.add(new TeamSnapshot(protocolTeamId, displayName, team.getColorName(),
                    team.getColorCode(), members, teamPoints));
            for (UUID member : members) {
                String username = plugin.getPlayerManager().getPlayerName(member);
                if (username == null || username.isBlank()) username = member.toString();
                double playerPoints = runMode == GameRunMode.EVENT ? plugin.getRankManager().getPlayerPoints(member) : 0D;
                participants.add(new PlayerSnapshot(member, username,
                        ParticipantRole.PLAYER, protocolTeamId, org.bukkit.Bukkit.getPlayer(member) != null,
                        playerPoints));
            }
        }
        spectators.stream().sorted().forEach(uuid -> {
            String username = plugin.getPlayerManager().getPlayerName(uuid);
            if (username == null || username.isBlank()) username = uuid.toString();
            double playerPoints = runMode == GameRunMode.EVENT
                    ? plugin.getRankManager().getPlayerPoints(uuid) : 0D;
            participants.add(new PlayerSnapshot(uuid, username,
                    ParticipantRole.SPECTATOR, null, false, playerPoints));
        });

        int duration = runMode == GameRunMode.DAILY
                ? variant.durationSeconds(config.getTimer()) : config.getTimer();
        String configHash = BingoManifestHasher.hash(duration, cardSeed, scoring, runtimeRules, tasks);

        return new MatchManifest(ProtocolVersion.CURRENT, matchId, epoch, System.currentTimeMillis(), workerId,
                switch (runMode) {
                    case EVENT -> MatchRunMode.EVENT;
                    case DAILY -> MatchRunMode.DAILY;
                    case GAME -> MatchRunMode.GAME;
                },
                duration, cardSeed, configHash, scoring, runtimeRules, tasks,
                teamSnapshots, participants);
    }

    private BingoPresentation presentation(GameRunMode runMode) {
        MessageService lang = MessageService.global();
        Map<String, String> messages = new java.util.LinkedHashMap<>();
        messages.put("game.name", GameTypeEnum.Bingo.toString());
        messages.put("game.preparation-countdown", MessageConfig.GAME_PREPARATION_COUNT_DOWN);
        messages.put("game.introduction-title", MessageConfig.GAME_INTRODUCTION_TITLE);
        messages.put("game.start-countdown-title", MessageConfig.GAME_START_COUNT_DOWN_TITLE);
        messages.put("game.start-countdown-subtitle", MessageConfig.GAME_START_COUNT_DOWN_SUBTITLE);
        messages.put("game.start-action-bar", MessageConfig.GAME_START_ACTION_BAR);
        messages.put("game.end-action-bar", MessageConfig.GAME_END_ACTION_BAR);
        boolean hasNextRound = runMode == GameRunMode.EVENT && plugin.getScheduleManager() != null
                && plugin.getScheduleManager().hasNextRound(GameTypeEnum.Bingo);
        messages.put("game.completion-title", hasNextRound
                ? MessageConfig.GAME_ROUND_COMPLETE_TITLE : MessageConfig.GAME_ROUND_END_TITLE);
        messages.put("bingo.start-preparation", MessageConfig.BINGO_START_PREPARATION);
        messages.put("bingo.start-preparation-title", MessageConfig.BINGO_START_PREPARATION_TITLE);
        messages.put("bingo.start-preparation-subtitle", MessageConfig.BINGO_START_PREPARATION_SUBTITLE);
        messages.put("bingo.game-start", MessageConfig.BINGO_GAME_START);
        messages.put("bingo.game-start-title", MessageConfig.BINGO_GAME_START_TITLE);
        messages.put("bingo.game-start-subtitle", MessageConfig.BINGO_GAME_START_SUBTITLE);
        messages.put("bingo.game-end", MessageConfig.BINGO_GAME_END);
        messages.put("bingo.game-end-title", MessageConfig.BINGO_GAME_END_TITLE);
        messages.put("bingo.game-end-subtitle", MessageConfig.BINGO_GAME_END_SUBTITLE);
        messages.put("bingo.timer", MessageConfig.BINGO_ACTION_BAR_COUNT_DOWN);
        messages.put("bingo.pvp-protection", MessageConfig.BINGO_PVP_PROTECTION);
        messages.put("bingo.pvp-active", MessageConfig.BINGO_PVP_ACTIVE);
        messages.put("bingo.pvp-countdown", MessageConfig.BINGO_PVP_START_COUNT_DOWN);
        messages.put("bingo.pvp-started", MessageConfig.BINGO_PVP_STARTED);
        messages.put("bingo.task-completed", MessageConfig.BINGO_TASK_COMPLETED);
        messages.put("bingo.game-winner", MessageConfig.BINGO_GAME_WINNER);
        messages.put("papi.none", MessageConfig.PLACEHOLDER_NONE);
        messages.put("papi.spectator", MessageConfig.PLACEHOLDER_SPECTATOR);
        messages.put("sidebar.status.waiting", MessageConfig.AREA_STATUS_WAITING);
        messages.put("sidebar.status.loading", MessageConfig.AREA_STATUS_LOADING);
        messages.put("sidebar.status.preparation", MessageConfig.AREA_STATUS_PREPARATION);
        messages.put("sidebar.status.countdown", MessageConfig.AREA_STATUS_COUNTDOWN);
        messages.put("sidebar.status.progress", MessageConfig.AREA_STATUS_PROGRESS);
        messages.put("sidebar.status.stopping", MessageConfig.AREA_STATUS_STOPPING);
        messages.put("sidebar.status.end", MessageConfig.AREA_STATUS_END);
        for (String key : List.of("card.title", "card.win_hint", "card.map_name", "card.map_hint",
                "card.completed_by", "card.completed_at", "card.occupied_by", "compass.item_name",
                "compass.item_hint", "compass.menu_title", "compass.teammate_hint",
                "compass.no_teammates", "compass.target_offline", "compass.teleport_success",
                "spectator.tracking.name", "spectator.tracking.next", "spectator.tracking.stop",
                "spectator.tracking.stopped-feedback", "spectator.tracking.unavailable-feedback",
                "spectator.tracking.active-feedback", "spectator.speed.name", "spectator.speed.faster",
                "spectator.speed.slower", "spectator.speed.feedback",
                "board.title", "board.separator", "board.current_game", "board.remaining_time",
                "board.status_waiting", "board.status_preparing", "board.status_finished",
                "board.teams_header", "board.team_score", "board.own_team_score", "board.footer")) {
            messages.put(key, lang.tr(key));
        }
        if (plugin.getSidebarManager() != null) {
            messages.putAll(plugin.getSidebarManager().bingoWorkerPresentation());
        }
        if (runMode == GameRunMode.DAILY) {
            messages.put("sidebar.ranking-line",
                    "{rank.team-color}{rank.position}. {rank.team} &7({rank.tasks} 项)");
            messages.put("sidebar.own-ranking-line",
                    "{rank.team-color}&l▶ {rank.position}. {rank.team} &7({rank.tasks} 项)");
        }
        return new BingoPresentation(messages);
    }

    private static BingoLocationSnapshot location(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return null;
        BingoDimension dimension = switch (location.getWorld().getEnvironment()) {
            case NETHER -> BingoDimension.NETHER;
            case THE_END -> BingoDimension.THE_END;
            default -> BingoDimension.OVERWORLD;
        };
        return new BingoLocationSnapshot(dimension, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }
}
