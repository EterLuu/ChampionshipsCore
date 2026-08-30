package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reversible ownership layer for temporary native-team assignments.
 *
 * <p>An overlay remembers the team which owned each entry before assignment. On release it restores
 * that team only while the entry is still assigned to a team owned by this overlay. A later
 * assignment by another plugin is therefore left untouched.</p>
 */
public final class NativeTeamOverlay implements AutoCloseable {
    private final NativeTeamService nativeTeams;
    private final Set<Team> ownedTeams = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UUID, Assignment> assignments = new LinkedHashMap<>();

    public NativeTeamOverlay(NativeTeamService nativeTeams) {
        this.nativeTeams = Objects.requireNonNull(nativeTeams, "nativeTeams");
    }

    public void own(Team team) {
        ownedTeams.add(Objects.requireNonNull(team, "team"));
    }

    public void assign(UUID playerId, String entry, Team target) {
        assign(playerId, entry, target, null, false);
    }

    /** Assigns an entry while explicitly defining the team restored when this overlay is released. */
    public void assign(UUID playerId, String entry, Team target, Optional<String> originalTeam) {
        assign(playerId, entry, target, Objects.requireNonNull(originalTeam, "originalTeam").orElse(null), true);
    }

    private void assign(UUID playerId, String entry, Team target, String explicitOriginal, boolean hasExplicitOriginal) {
        Objects.requireNonNull(playerId, "playerId");
        if (entry == null || entry.isBlank()) throw new IllegalArgumentException("Native team entry must not be blank");
        Objects.requireNonNull(target, "target");
        if (!ownedTeams.contains(target)) throw new IllegalArgumentException("Target team is not owned by this overlay");

        Assignment existing = assignments.get(playerId);
        if (existing != null && !existing.entry().equals(entry)) {
            detachOwnedEntry(existing.entry());
        }
        String original;
        if (hasExplicitOriginal) {
            original = explicitOriginal;
        } else if (existing == null) {
            original = currentExternalTeam(entry);
        } else if (!existing.entry().equals(entry)) {
            String renamedEntryTeam = currentExternalTeam(entry);
            original = renamedEntryTeam == null ? existing.originalTeam() : renamedEntryTeam;
        } else {
            original = existing.originalTeam();
        }
        assignments.put(playerId, new Assignment(entry, target, original));
        target.addEntry(entry);
    }

    public boolean contains(UUID playerId) {
        return assignments.containsKey(playerId);
    }

    public boolean isEmpty() {
        return assignments.isEmpty();
    }

    public Optional<String> originalTeam(UUID playerId) {
        Assignment assignment = assignments.get(playerId);
        return assignment == null ? Optional.empty() : Optional.ofNullable(assignment.originalTeam());
    }

    public void release(UUID playerId) {
        Assignment assignment = assignments.remove(playerId);
        if (assignment == null) return;
        Team current = nativeTeams.scoreboard().getEntryTeam(assignment.entry());
        if (current != null && !ownedTeams.contains(current)) return;
        if (current != null) current.removeEntry(assignment.entry());
        if (assignment.originalTeam() == null) return;
        Team original = nativeTeams.scoreboard().getTeam(assignment.originalTeam());
        if (original != null) original.addEntry(assignment.entry());
    }

    /** Releases all assignments targeting this team, then unregisters it. */
    public void removeTeam(Team team) {
        if (team == null || !ownedTeams.remove(team)) return;
        for (Map.Entry<UUID, Assignment> entry : new ArrayList<>(assignments.entrySet())) {
            if (entry.getValue().target() == team) releaseWithOwnedTeam(entry.getKey(), team);
        }
        nativeTeams.unregister(team);
    }

    private void releaseWithOwnedTeam(UUID playerId, Team removingTeam) {
        Assignment assignment = assignments.remove(playerId);
        if (assignment == null) return;
        Team current = nativeTeams.scoreboard().getEntryTeam(assignment.entry());
        if (current != null && current != removingTeam && !ownedTeams.contains(current)) return;
        if (current != null) current.removeEntry(assignment.entry());
        if (assignment.originalTeam() == null) return;
        Team original = nativeTeams.scoreboard().getTeam(assignment.originalTeam());
        if (original != null) original.addEntry(assignment.entry());
    }

    @Override
    public void close() {
        for (UUID playerId : new ArrayList<>(assignments.keySet())) release(playerId);
        for (Team team : new ArrayList<>(ownedTeams)) nativeTeams.unregister(team);
        ownedTeams.clear();
        assignments.clear();
    }

    private String currentExternalTeam(String entry) {
        Team current = nativeTeams.scoreboard().getEntryTeam(entry);
        return current == null || ownedTeams.contains(current) ? null : current.getName();
    }

    private void detachOwnedEntry(String entry) {
        Team current = nativeTeams.scoreboard().getEntryTeam(entry);
        if (current != null && ownedTeams.contains(current)) current.removeEntry(entry);
    }

    private record Assignment(String entry, Team target, String originalTeam) {
    }
}
