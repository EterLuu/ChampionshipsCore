package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NativeTeamOverlayTest {
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
        private final Map<String, TeamState> teams = new HashMap<>();
        private final Map<String, Team> entries = new HashMap<>();
        private final Scoreboard scoreboard = proxy(Scoreboard.class, (method, args) -> switch (method.getName()) {
            case "getTeam" -> {
                TeamState state = teams.get(args[0]);
                yield state == null ? null : state.team;
            }
            case "getEntryTeam" -> entries.get(args[0]);
            case "getTeams" -> teams.values().stream().map(state -> state.team).collect(java.util.stream.Collectors.toSet());
            case "registerNewTeam" -> team((String) args[0]);
            default -> defaultValue(method.getReturnType());
        });

        Scoreboard scoreboard() {
            return scoreboard;
        }

        Team entryTeam(String entry) {
            return entries.get(entry);
        }

        Team team(String name) {
            if (teams.containsKey(name)) throw new IllegalArgumentException("Duplicate team " + name);
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
                this.team = proxy(Team.class, (method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getScoreboard" -> scoreboard;
                    case "getEntries" -> Set.copyOf(members);
                    case "hasEntry" -> members.contains(args[0]);
                    case "getSize" -> members.size();
                    case "addEntry" -> {
                        String entry = (String) args[0];
                        Team previous = entries.put(entry, self[0]);
                        if (previous != null && previous != self[0]) state(previous).members.remove(entry);
                        members.add(entry);
                        yield null;
                    }
                    case "removeEntry" -> {
                        String entry = (String) args[0];
                        boolean removed = members.remove(entry);
                        if (entries.get(entry) == self[0]) entries.remove(entry);
                        yield removed;
                    }
                    case "unregister" -> {
                        teams.remove(name, this);
                        for (String entry : Set.copyOf(members)) {
                            if (entries.get(entry) == self[0]) entries.remove(entry);
                        }
                        members.clear();
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
                self[0] = this.team;
            }
        }

        private TeamState state(Team team) {
            return teams.values().stream().filter(state -> state.team == team).findFirst().orElseThrow();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
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
