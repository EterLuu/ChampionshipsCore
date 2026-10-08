package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class RiptideTeamCollisionsTest {
    @Test
    void repeatedSpawnAndOneTeammateLeavingKeepCollisionsDisabledUntilLastRelease() {
        Board board = new Board();
        NativeTeam team = board.team("red", Team.OptionStatus.ALWAYS);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Player first = board.player(firstId, "First", team);
        Player second = board.player(secondId, "Second", team);
        var collisions = new RiptideTeamCollisions();

        collisions.disable(first);
        collisions.disable(second);
        collisions.disable(first);
        assertEquals(Team.OptionStatus.NEVER, team.rule);
        assertEquals(1, team.updates);
        collisions.restore(firstId);
        assertEquals(Team.OptionStatus.NEVER, team.rule);
        collisions.restore(secondId);
        assertEquals(Team.OptionStatus.ALWAYS, team.rule);
        collisions.restore(secondId);
        assertEquals(2, team.updates);
    }

    @Test
    void cleanupRestoresEachOriginalPolicyAndCanBeReusedInTheNextRound() {
        Board board = new Board();
        var collisions = new RiptideTeamCollisions();
        Map<NativeTeam, Team.OptionStatus> originals = new HashMap<>();
        for (Team.OptionStatus rule : Team.OptionStatus.values()) {
            NativeTeam team = board.team(rule.name(), rule);
            originals.put(team, rule);
            collisions.disable(board.player(UUID.randomUUID(), rule.name(), team));
            assertEquals(Team.OptionStatus.NEVER, team.rule);
        }
        collisions.close();
        collisions.close();
        originals.forEach((team, rule) -> assertEquals(rule, team.rule));

        NativeTeam next = board.team("next", Team.OptionStatus.FOR_OTHER_TEAMS);
        collisions.disable(board.player(UUID.randomUUID(), "Next", next));
        assertEquals(Team.OptionStatus.NEVER, next.rule);
        collisions.close();
        assertEquals(Team.OptionStatus.FOR_OTHER_TEAMS, next.rule);
    }

    @Test
    void changingTeamsOrRemovingMembershipRestoresThePreviousTeamWithoutReassigningEntries() {
        Board board = new Board();
        NativeTeam original = board.team("red", Team.OptionStatus.ALWAYS);
        NativeTeam replacement = board.team("blue", Team.OptionStatus.FOR_OWN_TEAM);
        Player player = board.player(UUID.randomUUID(), "First", original);
        var collisions = new RiptideTeamCollisions();
        collisions.disable(player);
        board.entries.put("First", replacement.team);
        collisions.disable(player);
        assertEquals(Team.OptionStatus.ALWAYS, original.rule);
        assertEquals(Team.OptionStatus.NEVER, replacement.rule);
        assertEquals(replacement.team, board.entries.get("First"));

        board.entries.remove("First");
        collisions.disable(player);
        assertEquals(Team.OptionStatus.FOR_OWN_TEAM, replacement.rule);
        collisions.close();
        assertEquals(Map.of(), board.entries);
    }

    @Test
    void cleanupDoesNotChangeRemovedOrReplacedTeamsOrAnotherCollisionPolicy() {
        Board board = new Board();
        var collisions = new RiptideTeamCollisions();
        NativeTeam removed = board.team("removed", Team.OptionStatus.ALWAYS);
        NativeTeam replaced = board.team("replaced", Team.OptionStatus.ALWAYS);
        NativeTeam changed = board.team("changed", Team.OptionStatus.ALWAYS);
        for (NativeTeam team : new NativeTeam[] {removed, replaced, changed})
            collisions.disable(board.player(UUID.randomUUID(), team.name, team));

        board.teams.remove(removed.name);
        NativeTeam replacement = board.team(replaced.name, Team.OptionStatus.FOR_OWN_TEAM);
        changed.rule = Team.OptionStatus.FOR_OTHER_TEAMS;
        collisions.close();
        assertEquals(Team.OptionStatus.NEVER, removed.rule);
        assertEquals(Team.OptionStatus.NEVER, replaced.rule);
        assertEquals(Team.OptionStatus.FOR_OWN_TEAM, replacement.rule);
        assertEquals(Team.OptionStatus.FOR_OTHER_TEAMS, changed.rule);
        assertEquals(1, removed.updates);
        assertEquals(1, replaced.updates);
        assertEquals(0, replacement.updates);
        assertEquals(1, changed.updates);
    }

    private static final class Board {
        final Map<String, Team> teams = new HashMap<>();
        final Map<String, Team> entries = new HashMap<>();
        final Scoreboard scoreboard =
                proxy(
                        Scoreboard.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getEntryTeam" -> entries.get(args[0]);
                                    case "getTeam" -> teams.get(args[0]);
                                    default -> throw new AssertionError(method.getName());
                                });

        NativeTeam team(String name, Team.OptionStatus rule) {
            NativeTeam team = new NativeTeam(name, rule);
            teams.put(name, team.team);
            return team;
        }

        Player player(UUID id, String name, NativeTeam team) {
            entries.put(name, team.team);
            return proxy(
                    Player.class,
                    (object, method, args) ->
                            switch (method.getName()) {
                                case "getUniqueId" -> id;
                                case "getName" -> name;
                                case "getScoreboard" -> scoreboard;
                                default -> throw new AssertionError(method.getName());
                            });
        }
    }

    private static final class NativeTeam {
        final String name;
        final Team team;
        Team.OptionStatus rule;
        int updates;

        NativeTeam(String name, Team.OptionStatus rule) {
            this.name = name;
            this.rule = rule;
            team =
                    proxy(
                            Team.class,
                            (object, method, args) ->
                                    switch (method.getName()) {
                                        case "hashCode" -> System.identityHashCode(object);
                                        case "equals" -> object == args[0];
                                        case "toString", "getName" -> name;
                                        case "getOption" -> {
                                            assertEquals(Team.Option.COLLISION_RULE, args[0]);
                                            yield this.rule;
                                        }
                                        case "setOption" -> {
                                            assertEquals(Team.Option.COLLISION_RULE, args[0]);
                                            this.rule = (Team.OptionStatus) args[1];
                                            updates++;
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Team identity and presentation must be"
                                                                + " preserved: "
                                                                + method.getName());
                                    });
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
