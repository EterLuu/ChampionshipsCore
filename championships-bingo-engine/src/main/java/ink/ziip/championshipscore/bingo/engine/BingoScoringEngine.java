package ink.ziip.championshipscore.bingo.engine;

import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.CompletionObservation;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure, deterministic Bingo scoring state machine shared by the worker and SCC replay path.
 *
 * <p>The owner must submit accepted observations in worker sequence order. The class synchronizes
 * its public state transitions as a defensive boundary, but callers should still give one match a
 * single lightweight coordinator instead of performing Bukkit work while holding this monitor.
 */
public final class BingoScoringEngine {
    private final UUID matchId;
    private final long epoch;
    private final int cellCount;
    private final BingoScoringRules rules;
    private final Map<Integer, TeamSnapshot> teams;
    private final Map<UUID, Integer> participants;
    private final List<Set<Integer>> taskClaims;
    private final Map<Integer, BitSet> completedByTeam = new HashMap<>();
    private final Map<Integer, Integer> awardedLines = new HashMap<>();
    private final Map<Integer, Integer> teamScores = new HashMap<>();
    private final Map<Integer, Long> lastCompletionTicks = new HashMap<>();
    private final Map<Long, CompletionObservation> observations = new LinkedHashMap<>();
    private final Map<Long, ScoringDecision> decisions = new LinkedHashMap<>();
    private long lastSeq;

    public BingoScoringEngine(MatchManifest manifest) {
        this(
                manifest.matchId(),
                manifest.epoch(),
                manifest.scoring(),
                manifest.teams(),
                participantTeams(manifest.participants()));
    }

    /** Scoring inputs shared by local rounds and remote replay; no world or transport is needed. */
    public BingoScoringEngine(
            UUID matchId,
            long epoch,
            BingoScoringRules rules,
            List<TeamSnapshot> teams,
            Map<UUID, Integer> participants) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        if (epoch < 1) throw new IllegalArgumentException("epoch must be positive");
        this.epoch = epoch;
        this.rules = Objects.requireNonNull(rules, "rules");
        this.cellCount = Math.multiplyExact(rules.cardWidth(), rules.cardWidth());
        var byId = new HashMap<Integer, TeamSnapshot>();
        var members = new HashMap<UUID, Integer>();
        for (TeamSnapshot team : teams) {
            if (byId.putIfAbsent(team.id(), team) != null)
                throw new IllegalArgumentException("Duplicate scoring team: " + team.id());
            for (UUID member : team.members())
                if (members.putIfAbsent(member, team.id()) != null)
                    throw new IllegalArgumentException(
                            "Member belongs to multiple scoring teams: " + member);
        }
        this.teams = Map.copyOf(byId);
        this.participants = Map.copyOf(participants);
        this.participants.forEach(
                (player, team) -> {
                    if (!team.equals(members.get(player)))
                        throw new IllegalArgumentException(
                                "Scoring participant is not in the frozen team: " + player);
                });
        this.taskClaims = new ArrayList<>(cellCount);
        for (int ignored = 0; ignored < cellCount; ignored++) taskClaims.add(new LinkedHashSet<>());
        for (TeamSnapshot team : teams) {
            completedByTeam.put(team.id(), new BitSet(cellCount));
            awardedLines.put(team.id(), 0);
            teamScores.put(team.id(), 0);
            lastCompletionTicks.put(team.id(), Long.MAX_VALUE);
        }
    }

    private static Map<UUID, Integer> participantTeams(List<PlayerSnapshot> players) {
        var result = new HashMap<UUID, Integer>();
        for (PlayerSnapshot player : players)
            if (player.role() == ParticipantRole.PLAYER) result.put(player.uuid(), player.teamId());
        return result;
    }

    public synchronized ScoringDecision apply(CompletionObservation observation) {
        validateIdentity(observation);
        if (observation.seq() <= lastSeq) {
            CompletionObservation previous = observations.get(observation.seq());
            if (!observation.equals(previous)) {
                throw new IllegalStateException(
                        "Sequence " + observation.seq() + " was replayed with different data");
            }
            return decisions.get(observation.seq());
        }
        if (observation.seq() != lastSeq + 1) {
            throw new IllegalStateException(
                    "Completion sequence gap: expected "
                            + (lastSeq + 1)
                            + " but received "
                            + observation.seq());
        }

        ScoringDecision decision = decide(observation);
        lastSeq = observation.seq();
        observations.put(observation.seq(), observation);
        decisions.put(observation.seq(), decision);
        return decision;
    }

    private ScoringDecision decide(CompletionObservation observation) {
        TeamSnapshot team = teams.get(observation.teamId());
        if (team == null) return rejected(observation, "unknown-team");
        if (observation.cellIndex() >= taskClaims.size())
            return rejected(observation, "unknown-cell");

        if (!Integer.valueOf(team.id()).equals(participants.get(observation.playerId()))) {
            return rejected(observation, "player-not-in-team");
        }

        String rejection = rejectionReason(team.id(), observation.cellIndex());
        if (rejection != null) return rejected(observation, rejection);
        BitSet completed = completedByTeam.get(team.id());
        Set<Integer> claims = taskClaims.get(observation.cellIndex());
        int claimRank = claims.size();
        claims.add(team.id());
        completed.set(observation.cellIndex());
        if (rules.variant().remix() == BingoRemix.COOP) {
            claims.addAll(teams.keySet());
            for (Map.Entry<Integer, BitSet> entry : completedByTeam.entrySet()) {
                entry.getValue().set(observation.cellIndex());
                teamScores.put(entry.getKey(), entry.getValue().cardinality());
                lastCompletionTicks.put(entry.getKey(), observation.observedGameTick());
            }
        }

        boolean pointsMode =
                rules.variant().mode().usesPoints() && rules.variant().remix() != BingoRemix.COOP;
        // Non-points modes still emit one bookkeeping point per completed cell so Core's existing
        // DAILY result pipeline can rank transient teams without a second scoring transport.
        int cellPoints = pointsMode ? rules.pointsForClaimRank(claimRank) : 1;
        int completedLines = countCompletedLines(completed);
        int previousLines = awardedLines.get(team.id());
        int linePoints = 0;
        if (pointsMode) {
            for (int lineIndex = previousLines; lineIndex < completedLines; lineIndex++) {
                linePoints +=
                        lineIndex < rules.lineBonusMajorCount()
                                ? rules.lineBonus()
                                : rules.lineBonusMinor();
            }
        }
        awardedLines.put(team.id(), completedLines);

        List<PlayerAward> awards = new ArrayList<>();
        if (cellPoints > 0) {
            if (rules.variant().remix() == BingoRemix.COOP) {
                for (TeamSnapshot collaborator : teams.values()) {
                    if (!collaborator.members().isEmpty())
                        awards.add(
                                new PlayerAward(
                                        collaborator.members().getFirst(),
                                        1,
                                        "coop-cell:" + observation.cellIndex()));
                }
            } else {
                awards.add(
                        new PlayerAward(
                                observation.playerId(),
                                cellPoints,
                                "cell:" + observation.cellIndex()));
            }
        }
        if (linePoints > 0) {
            for (UUID member : team.members()) {
                awards.add(new PlayerAward(member, linePoints, "line:" + completedLines));
            }
        }

        int teamDelta = cellPoints + linePoints * Math.max(1, team.members().size());
        int teamScore;
        if (pointsMode) teamScore = teamScores.merge(team.id(), teamDelta, Integer::sum);
        else {
            teamScore = completed.cardinality();
            teamScores.put(team.id(), teamScore);
        }
        lastCompletionTicks.put(team.id(), observation.observedGameTick());
        return new ScoringDecision(
                observation,
                true,
                "",
                claimRank,
                cellPoints,
                linePoints,
                completedLines,
                teamScore,
                awards);
    }

    public synchronized boolean canAttempt(int teamId, int cellIndex) {
        return rejectionReason(teamId, cellIndex) == null;
    }

    private String rejectionReason(int teamId, int cellIndex) {
        BitSet completed = completedByTeam.get(teamId);
        if (completed == null) return "unknown-team";
        if (cellIndex < 0 || cellIndex >= cellCount) return "unknown-cell";
        if (completed.get(cellIndex)) return "already-completed-by-team";
        if (rules.variant().mode().locksCells() && !taskClaims.get(cellIndex).isEmpty())
            return "cell-locked-by-other-team";
        if (rules.variant().remix() == BingoRemix.CHAIN
                && !completed.isEmpty()
                && !adjacentToCompleted(completed, cellIndex)) return "chain-cell-not-reachable";
        return null;
    }

    public synchronized int score(int teamId) {
        return teamScores.getOrDefault(teamId, 0);
    }

    public synchronized boolean hasCompleted(int teamId, int cellIndex) {
        BitSet cells = completedByTeam.get(teamId);
        return cells != null && cellIndex >= 0 && cellIndex < cellCount && cells.get(cellIndex);
    }

    public synchronized int completedCount(int teamId) {
        BitSet cells = completedByTeam.get(teamId);
        return cells == null ? 0 : cells.cardinality();
    }

    public synchronized int[] completedIndices(int teamId) {
        BitSet cells = completedByTeam.get(teamId);
        return cells == null ? new int[0] : cells.stream().toArray();
    }

    public synchronized int completedLines(int teamId) {
        BitSet cells = completedByTeam.get(teamId);
        return cells == null ? 0 : countCompletedLines(cells);
    }

    public synchronized long lastCompletionTick(int teamId) {
        return lastCompletionTicks.getOrDefault(teamId, Long.MAX_VALUE);
    }

    /** Counts first accepted claims, including equal-time observations in sequence order. */
    public synchronized int firstClaimCount(int teamId) {
        return (int)
                taskClaims.stream()
                        .filter(claims -> !claims.isEmpty() && claims.iterator().next() == teamId)
                        .count();
    }

    public synchronized boolean boardFullyClaimed() {
        return taskClaims.stream().allMatch(claims -> !claims.isEmpty());
    }

    private boolean adjacentToCompleted(BitSet completed, int cellIndex) {
        int width = rules.cardWidth();
        int x = cellIndex % width;
        int y = cellIndex / width;
        return (x > 0 && completed.get(cellIndex - 1))
                || (x + 1 < width && completed.get(cellIndex + 1))
                || (y > 0 && completed.get(cellIndex - width))
                || (y + 1 < width && completed.get(cellIndex + width));
    }

    public synchronized boolean hasWon(int teamId) {
        BitSet completed = completedByTeam.get(teamId);
        if (completed == null) return false;
        if (rules.variant().remix() == BingoRemix.COOP) return completed.cardinality() == cellCount;
        BingoMode mode = rules.variant().mode();
        if (mode.linesWin()) return countCompletedLines(completed) >= rules.variant().winLines();
        return mode.fullCardWins() && completed.cardinality() == cellCount;
    }

    private ScoringDecision rejected(CompletionObservation observation, String reason) {
        int teamId = observation.teamId();
        return new ScoringDecision(
                observation,
                false,
                reason,
                -1,
                0,
                0,
                awardedLines.getOrDefault(teamId, 0),
                teamScores.getOrDefault(teamId, 0),
                List.of());
    }

    private void validateIdentity(CompletionObservation observation) {
        java.util.Objects.requireNonNull(observation, "observation");
        if (!matchId.equals(observation.matchId()) || epoch != observation.epoch()) {
            throw new IllegalArgumentException(
                    "Observation belongs to a different match or fencing epoch");
        }
    }

    private int countCompletedLines(BitSet completed) {
        int width = rules.cardWidth();
        int lines = 0;
        for (int y = 0; y < width; y++) {
            boolean row = true;
            boolean column = true;
            for (int x = 0; x < width; x++) {
                row &= completed.get(width * y + x);
                column &= completed.get(width * x + y);
            }
            if (row) lines++;
            if (column) lines++;
        }
        boolean mainDiagonal = true;
        boolean antiDiagonal = true;
        for (int i = 0; i < width; i++) {
            mainDiagonal &= completed.get(i * (width + 1));
            antiDiagonal &= completed.get((i + 1) * (width - 1));
        }
        if (mainDiagonal) lines++;
        if (antiDiagonal) lines++;
        return lines;
    }

    public synchronized BingoResult result() {
        Map<Integer, Integer> completedCells = new TreeMap<>();
        completedByTeam.forEach((teamId, cells) -> completedCells.put(teamId, cells.cardinality()));
        Map<Integer, Integer> orderedScores = new TreeMap<>(teamScores);
        Map<Integer, Long> orderedTimes = new TreeMap<>(lastCompletionTicks);
        boolean fullyClaimed = boardFullyClaimed();
        return new BingoResult(
                lastSeq,
                fullyClaimed,
                orderedScores,
                completedCells,
                orderedTimes,
                hashResult(lastSeq, fullyClaimed, orderedScores, completedCells, orderedTimes));
    }

    private String hashResult(
            long seq,
            boolean fullyClaimed,
            Map<Integer, Integer> scores,
            Map<Integer, Integer> completedCells,
            Map<Integer, Long> completionTicks) {
        String canonical =
                matchId
                        + "\n"
                        + epoch
                        + "\n"
                        + seq
                        + "\n"
                        + fullyClaimed
                        + "\n"
                        + scores
                        + "\n"
                        + completedCells
                        + "\n"
                        + completionTicks;
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
