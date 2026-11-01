package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class NativeTeamOverlayTest {
    @Test
    void freshBukkitWrappersStillRestoreTheOriginalRegistration() {
        FakeScoreboard scoreboard = new FakeScoreboard();
        scoreboard.freshWrappers = true;
        Team original = scoreboard.team("formal");
        Team temporary = scoreboard.team("temporary");
        original.addEntry("Alice");
        var overlay = new NativeTeamService(scoreboard.scoreboard()).newOverlay();
        overlay.own(temporary);
        UUID id = UUID.randomUUID();
        overlay.assign(id, "Alice", temporary);
        // A lookup returns a different wrapper with Bukkit registration equality.
        Team lookedUp = scoreboard.scoreboard().getTeam("temporary");
        org.junit.jupiter.api.Assertions.assertNotSame(temporary, lookedUp);
        assertEquals(temporary, lookedUp);
        overlay.removeTeam(lookedUp);
        assertSame(original, scoreboard.entryTeam("Alice"));
        assertNull(scoreboard.scoreboard().getTeam("temporary"));
    }

    @Test
    void anotherRegistrationWithTheSameNameIsNeverOwnedByTheOldOverlay() {
        FakeScoreboard scoreboard = new FakeScoreboard();
        scoreboard.freshWrappers = true;
        Team temporary = scoreboard.team("temporary");
        var overlay = new NativeTeamService(scoreboard.scoreboard()).newOverlay();
        overlay.own(temporary);
        UUID id = UUID.randomUUID();
        overlay.assign(id, "Alice", temporary);
        temporary.unregister();
        Team replacement = scoreboard.team("temporary");
        replacement.addEntry("Alice");
        overlay.release(id);
        assertSame(replacement, scoreboard.entryTeam("Alice"));
    }

    @Test
    void repeatedAssignmentSkipsNativeEntryWritesButRepairsExternalReassignment() {
        FakeScoreboard scoreboard = new FakeScoreboard();
        Team original = scoreboard.team("formal");
        Team temporary = scoreboard.team("temporary");
        original.addEntry("Alice");
        var overlay = new NativeTeamService(scoreboard.scoreboard()).newOverlay();
        overlay.own(temporary);
        UUID id = UUID.randomUUID();
        overlay.assign(id, "Alice", temporary);
        int writes = scoreboard.entryWrites;
        for (int i = 0; i < 64; i++) overlay.assign(id, "Alice", temporary);
        assertEquals(writes, scoreboard.entryWrites);
        original.addEntry("Alice");
        overlay.assign(id, "Alice", temporary);
        assertSame(temporary, scoreboard.entryTeam("Alice"));
        overlay.release(id);
        assertSame(original, scoreboard.entryTeam("Alice"));
    }

    @Test
    void restoresTheOriginalTeamWhenReleased() {
        FakeScoreboard scoreboard = new FakeScoreboard();
        Team original = scoreboard.team("formal");
        Team temporary = scoreboard.team("temporary");
        original.addEntry("Alice");
        NativeTeamOverlay overlay = new NativeTeamService(scoreboard.scoreboard()).newOverlay();
        overlay.own(temporary);

        UUID playerId = UUID.randomUUID();
        overlay.assign(playerId, "Alice", temporary);
        assertSame(temporary, scoreboard.entryTeam("Alice"));

        overlay.release(playerId);
        assertSame(original, scoreboard.entryTeam("Alice"));
    }

    @Test
    void leavesAThirdPartyReassignmentUntouched() {
        FakeScoreboard scoreboard = new FakeScoreboard();
        Team original = scoreboard.team("formal");
        Team temporary = scoreboard.team("temporary");
        Team external = scoreboard.team("external");
        original.addEntry("Alice");
        NativeTeamOverlay overlay = new NativeTeamService(scoreboard.scoreboard()).newOverlay();
        overlay.own(temporary);
        UUID playerId = UUID.randomUUID();
        overlay.assign(playerId, "Alice", temporary);

        external.addEntry("Alice");
        overlay.release(playerId);

        assertSame(external, scoreboard.entryTeam("Alice"));
    }

    @Test
    void carriesTheOriginalTeamAcrossAUsernameChange() {
        FakeScoreboard scoreboard = new FakeScoreboard();
        Team original = scoreboard.team("formal");
        Team temporary = scoreboard.team("temporary");
        original.addEntry("OldName");
        NativeTeamOverlay overlay = new NativeTeamService(scoreboard.scoreboard()).newOverlay();
        overlay.own(temporary);
        UUID playerId = UUID.randomUUID();
        overlay.assign(playerId, "OldName", temporary);

        overlay.assign(playerId, "NewName", temporary);
        assertNull(scoreboard.entryTeam("OldName"));
        assertSame(temporary, scoreboard.entryTeam("NewName"));

        overlay.release(playerId);
        assertSame(original, scoreboard.entryTeam("NewName"));
        assertNull(scoreboard.entryTeam("OldName"));
    }

    @Test
    void removingAnOwnedTeamRestoresAssignmentsAndUnregistersIt() {
        FakeScoreboard scoreboard = new FakeScoreboard();
        Team original = scoreboard.team("formal");
        Team temporary = scoreboard.team("temporary");
        original.addEntry("Alice");
        NativeTeamOverlay overlay = new NativeTeamService(scoreboard.scoreboard()).newOverlay();
        overlay.own(temporary);
        overlay.assign(UUID.randomUUID(), "Alice", temporary);

        overlay.removeTeam(temporary);

        assertSame(original, scoreboard.entryTeam("Alice"));
        assertNull(scoreboard.scoreboard().getTeam("temporary"));
    }

    private static final class FakeScoreboard {
        int entryWrites;
        boolean freshWrappers;
        final Map<Object, Team> wrapperRoots = new IdentityHashMap<>();
        private final Map<String, TeamState> teams = new HashMap<>();
        private final Map<String, Team> entries = new HashMap<>();
        private final Scoreboard scoreboard =
                proxy(
                        Scoreboard.class,
                        (method, args) ->
                                switch (method.getName()) {
                                    case "getTeam" -> {
                                        TeamState state = teams.get(args[0]);
                                        yield state == null ? null : wrap(state.team);
                                    }
                                    case "getEntryTeam" -> wrap(entries.get(args[0]));
                                    case "getTeams" ->
                                            teams.values().stream()
                                                    .map(state -> state.team)
                                                    .collect(java.util.stream.Collectors.toSet());
                                    case "registerNewTeam" -> team((String) args[0]);
                                    default -> defaultValue(method.getReturnType());
                                });

        Scoreboard scoreboard() {
            return scoreboard;
        }

        Team entryTeam(String entry) {
            return entries.get(entry);
        }

        private Team wrap(Team root) {
            if (root == null || !freshWrappers) return root;
            Team wrapper =
                    proxy(
                            Team.class,
                            (method, args) -> {
                                try {
                                    return method.invoke(root, args);
                                } catch (java.lang.reflect.InvocationTargetException failure) {
                                    throw failure.getCause();
                                }
                            });
            wrapperRoots.put(wrapper, root);
            return wrapper;
        }

        Team team(String name) {
            if (teams.containsKey(name))
                throw new IllegalArgumentException("Duplicate team " + name);
            TeamState state = new TeamState(name);
            teams.put(name, state);
            return state.team;
        }

        private final class TeamState {
            private final String name;
            private final Set<String> members = new HashSet<>();
            private final Team team;

            private TeamState(String name) {
                this.name = name;
                Team[] self = new Team[1];
                this.team =
                        proxy(
                                Team.class,
                                (method, args) ->
                                        switch (method.getName()) {
                                            case "hashCode" -> System.identityHashCode(self[0]);
                                            case "equals" -> wrapperRoots.get(args[0]) == self[0];
                                            case "getName" -> name;
                                            case "getScoreboard" -> scoreboard;
                                            case "getEntries" -> Set.copyOf(members);
                                            case "hasEntry" -> members.contains(args[0]);
                                            case "getSize" -> members.size();
                                            case "addEntry" -> {
                                                entryWrites++;
                                                String entry = (String) args[0];
                                                Team previous = entries.put(entry, self[0]);
                                                if (previous != null && previous != self[0])
                                                    state(previous).members.remove(entry);
                                                members.add(entry);
                                                yield null;
                                            }
                                            case "removeEntry" -> {
                                                String entry = (String) args[0];
                                                boolean removed = members.remove(entry);
                                                if (entries.get(entry) == self[0])
                                                    entries.remove(entry);
                                                yield removed;
                                            }
                                            case "unregister" -> {
                                                teams.remove(name, this);
                                                for (String entry : Set.copyOf(members)) {
                                                    if (entries.get(entry) == self[0])
                                                        entries.remove(entry);
                                                }
                                                members.clear();
                                                yield null;
                                            }
                                            default -> defaultValue(method.getReturnType());
                                        });
                self[0] = this.team;
                wrapperRoots.put(this.team, this.team);
            }
        }

        private TeamState state(Team team) {
            return teams.values().stream()
                    .filter(state -> state.team == team)
                    .findFirst()
                    .orElseThrow();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (ignored, method, args) -> invocation.invoke(method, args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        throw new IllegalArgumentException("Unsupported primitive " + type);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
