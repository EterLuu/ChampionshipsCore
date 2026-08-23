package ink.ziip.championshipscore.bingo.engine;

import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure, deterministic Bingo scoring state machine shared by the worker and SCC replay path.
 *
 * <p>The owner must submit accepted observations in worker sequence order. The class synchronizes its
 * public state transitions as a defensive boundary, but callers should still give one match a single
 * lightweight coordinator instead of performing Bukkit work while holding this monitor.</p>
 */
public final class BingoScoringEngine {
    private final MatchManifest manifest;
    private final BingoScoringRules rules;
    private final Map<Integer, TeamSnapshot> teams;
    private final Map<UUID, PlayerSnapshot> players;
    private final List<Set<Integer>> taskClaims;
    private final Map<Integer, BitSet> completedByTeam = new HashMap<>();
    private final Map<Integer, Integer> awardedLines = new HashMap<>();
    private final Map<Integer, Integer> teamScores = new HashMap<>();
    private final Map<Integer, Long> lastCompletionTicks = new HashMap<>();
    private final Map<Long, CompletionObservation> observations = new LinkedHashMap<>();
    private final Map<Long, ScoringDecision> decisions = new LinkedHashMap<>();
    private long lastSeq;

    public BingoScoringEngine(MatchManifest manifest) {
        this.manifest = java.util.Objects.requireNonNull(manifest, "manifest");
        this.rules = manifest.scoring();
        this.teams = manifest.teamsById();
        this.players = new HashMap<>();
        for (PlayerSnapshot participant : manifest.participants()) {
            players.put(participant.uuid(), participant);
        }
        this.taskClaims = new ArrayList<>(manifest.tasks().size());
        for (int ignored = 0; ignored < manifest.tasks().size(); ignored++) {
            taskClaims.add(new HashSet<>());
        }
        for (TeamSnapshot team : manifest.teams()) {
            completedByTeam.put(team.id(), new BitSet(manifest.tasks().size()));
            awardedLines.put(team.id(), 0);
            teamScores.put(team.id(), 0);
            lastCompletionTicks.put(team.id(), Long.MAX_VALUE);
        }
    }

    public synchronized ScoringDecision apply(CompletionObservation observation) {
        validateIdentity(observation);
        if (observation.seq() <= lastSeq) {
            CompletionObservation previous = observations.get(observation.seq());
            if (!observation.equals(previous)) {
                throw new IllegalStateException("Sequence " + observation.seq() + " was replayed with different data");
            }
            return decisions.get(observation.seq());
        }
        if (observation.seq() != lastSeq + 1) {
            throw new IllegalStateException(
                    "Completion sequence gap: expected " + (lastSeq + 1) + " but received " + observation.seq());
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
        if (observation.cellIndex() >= taskClaims.size()) return rejected(observation, "unknown-cell");

        PlayerSnapshot player = players.get(observation.playerId());
        if (player == null || player.role() != ParticipantRole.PLAYER
                || !Integer.valueOf(team.id()).equals(player.teamId())) {
            return rejected(observation, "player-not-in-team");
        }

        BitSet completed = completedByTeam.get(team.id());
        if (completed.get(observation.cellIndex())) {
            return rejected(observation, "already-completed-by-team");
        }

        Set<Integer> claims = taskClaims.get(observation.cellIndex());
        if (rules.variant().mode().locksCells() && !claims.isEmpty()) {
            return rejected(observation, "cell-locked-by-other-team");
        }
        if (rules.variant().remix() == ink.ziip.championshipscore.protocol.BingoRemix.CHAIN
                && !completed.isEmpty() && !adjacentToCompleted(completed, observation.cellIndex())) {
            return rejected(observation, "chain-cell-not-reachable");
        }
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

        boolean pointsMode = rules.variant().mode().usesPoints()
                && rules.variant().remix() != BingoRemix.COOP;
        // Non-points modes still emit one bookkeeping point per completed cell so Core's existing
        // DAILY result pipeline can rank transient teams without a second scoring transport.
        int cellPoints = pointsMode ? rules.pointsForClaimRank(claimRank) : 1;
        int completedLines = countCompletedLines(completed);
        int previousLines = awardedLines.get(team.id());
        int linePoints = 0;
        if (pointsMode) {
            for (int lineIndex = previousLines; lineIndex < completedLines; lineIndex++) {
                linePoints += lineIndex < rules.lineBonusMajorCount()
                        ? rules.lineBonus() : rules.lineBonusMinor();
            }
        }
        awardedLines.put(team.id(), completedLines);

        List<PlayerAward> awards = new ArrayList<>();
        if (cellPoints > 0) {
            if (rules.variant().remix() == BingoRemix.COOP) {
                for (TeamSnapshot collaborator : teams.values()) {
                    if (!collaborator.members().isEmpty()) awards.add(new PlayerAward(
                            collaborator.members().getFirst(), 1, "coop-cell:" + observation.cellIndex()));
                }
            } else {
                awards.add(new PlayerAward(observation.playerId(), cellPoints,
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
        return new ScoringDecision(observation, true, "", claimRank, cellPoints, linePoints,
                completedLines, teamScore, awards);
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
        if (rules.variant().remix() == BingoRemix.COOP)
            return completed.cardinality() == manifest.tasks().size();
        BingoMode mode = rules.variant().mode();
        if (mode.linesWin()) return countCompletedLines(completed) >= rules.variant().winLines();
        return mode.fullCardWins() && completed.cardinality() == manifest.tasks().size();
    }

    private ScoringDecision rejected(CompletionObservation observation, String reason) {
        int teamId = observation.teamId();
        return new ScoringDecision(observation, false, reason, -1, 0, 0,
                awardedLines.getOrDefault(teamId, 0), teamScores.getOrDefault(teamId, 0), List.of());
    }

    private void validateIdentity(CompletionObservation observation) {
        java.util.Objects.requireNonNull(observation, "observation");
        if (!manifest.matchId().equals(observation.matchId()) || manifest.epoch() != observation.epoch()) {
            throw new IllegalArgumentException("Observation belongs to a different match or fencing epoch");
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
        boolean fullyClaimed = taskClaims.stream().allMatch(claims -> !claims.isEmpty());
        return new BingoResult(lastSeq, fullyClaimed, orderedScores, completedCells, orderedTimes,
                hashResult(lastSeq, fullyClaimed, orderedScores, completedCells, orderedTimes));
    }

    private String hashResult(long seq, boolean fullyClaimed, Map<Integer, Integer> scores,
                              Map<Integer, Integer> completedCells, Map<Integer, Long> completionTicks) {
        String canonical = manifest.matchId() + "\n" + manifest.epoch() + "\n" + seq + "\n"
                + fullyClaimed + "\n" + scores + "\n" + completedCells + "\n" + completionTicks;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
