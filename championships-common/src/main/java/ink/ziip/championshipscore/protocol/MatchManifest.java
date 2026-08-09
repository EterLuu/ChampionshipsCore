package ink.ziip.championshipscore.protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Complete immutable input required to prepare and replay one Bingo match. */
public record MatchManifest(
        int protocolVersion,
        UUID matchId,
        long epoch,
        long createdAtEpochMilli,
        String workerId,
        MatchRunMode runMode,
        int durationSeconds,
        long cardSeed,
        String configHash,
        BingoScoringRules scoring,
        BingoRuntimeRules runtimeRules,
        List<BingoTaskSpec> tasks,
        List<TeamSnapshot> teams,
        List<PlayerSnapshot> participants
) {
    public MatchManifest {
        ProtocolVersion.requireSupported(protocolVersion);
        ProtocolSupport.required(matchId, "matchId");
        if (epoch < 1) throw new IllegalArgumentException("epoch must be positive");
        if (createdAtEpochMilli < 1) throw new IllegalArgumentException("createdAtEpochMilli must be positive");
        workerId = ProtocolSupport.nonBlank(workerId, "workerId");
        ProtocolSupport.required(runMode, "runMode");
        if (durationSeconds < 1) throw new IllegalArgumentException("durationSeconds must be positive");
        configHash = ProtocolSupport.nonBlank(configHash, "configHash");
        ProtocolSupport.required(scoring, "scoring");
        ProtocolSupport.required(runtimeRules, "runtimeRules");
        tasks = ProtocolSupport.immutableList(tasks, "tasks");
        teams = ProtocolSupport.immutableList(teams, "teams");
        participants = ProtocolSupport.immutableList(participants, "participants");

        int expectedCells = Math.multiplyExact(scoring.cardWidth(), scoring.cardWidth());
        if (tasks.size() != expectedCells) {
            throw new IllegalArgumentException("tasks must contain exactly " + expectedCells + " card cells");
        }

        Set<Integer> cellIndexes = new HashSet<>();
        Set<String> taskIds = new HashSet<>();
        for (BingoTaskSpec task : tasks) {
            if (task.cellIndex() >= expectedCells || !cellIndexes.add(task.cellIndex())) {
                throw new IllegalArgumentException("task cell indexes must be unique and inside the card");
            }
            if (!taskIds.add(task.taskId())) {
                throw new IllegalArgumentException("taskId must be unique within a card: " + task.taskId());
            }
        }

        Map<Integer, TeamSnapshot> teamById = new HashMap<>();
        Set<UUID> rosterMembers = new HashSet<>();
        for (TeamSnapshot team : teams) {
            if (teamById.put(team.id(), team) != null) {
                throw new IllegalArgumentException("team ids must be unique: " + team.id());
            }
            for (UUID member : team.members()) {
                if (!rosterMembers.add(member)) {
                    throw new IllegalArgumentException("a member cannot belong to multiple frozen teams: " + member);
                }
            }
        }
        Set<UUID> participantIds = new HashSet<>();
        for (PlayerSnapshot participant : participants) {
            if (!participantIds.add(participant.uuid())) {
                throw new IllegalArgumentException("participant UUIDs must be unique: " + participant.uuid());
            }
            if (participant.teamId() != null && !teamById.containsKey(participant.teamId())) {
                throw new IllegalArgumentException("participant references unknown team: " + participant.teamId());
            }
            if (participant.role() == ParticipantRole.PLAYER
                    && !teamById.get(participant.teamId()).members().contains(participant.uuid())) {
                throw new IllegalArgumentException("participant is not present in the frozen team membership");
            }
        }
    }

    public Map<Integer, TeamSnapshot> teamsById() {
        Map<Integer, TeamSnapshot> result = new HashMap<>();
        teams.forEach(team -> result.put(team.id(), team));
        return Map.copyOf(result);
    }
}
