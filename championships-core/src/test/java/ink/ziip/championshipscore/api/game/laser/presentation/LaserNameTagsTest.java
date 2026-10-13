package ink.ziip.championshipscore.api.game.laser.presentation;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class LaserNameTagsTest {
    @Test
    void existingTeamMembershipAndPresentationRemainOwnedByTheOriginalTeam() {
        var board = new Board();
        Team original = board.team("rank_001", Team.OptionStatus.ALWAYS, "one", "two");
        var state = new LaserNameTags.BoardState(board.proxy);
        Object game = new Object();
        state.update(game, Set.of("one", "two"));
        assertSame(original, board.proxy.getEntryTeam("one"));
        assertEquals(Set.of("one", "two"), original.getEntries());
        assertEquals("prefix", original.getPrefix());
        assertEquals("suffix", original.getSuffix());
        assertEquals(Team.OptionStatus.NEVER, original.getOption(Team.Option.NAME_TAG_VISIBILITY));
        state.release(game);
        assertEquals(Team.OptionStatus.ALWAYS, original.getOption(Team.Option.NAME_TAG_VISIBILITY));
        assertSame(original, board.proxy.getEntryTeam("one"));
    }

    @Test
    void oldGameReleaseDoesNotRestoreNamesStillHiddenByAnotherGame() {
        var board = new Board();
        Team original = board.team("rank_001", Team.OptionStatus.FOR_OTHER_TEAMS, "one");
        var state = new LaserNameTags.BoardState(board.proxy);
        Object oldGame = new Object();
        Object newGame = new Object();
        state.update(oldGame, Set.of("one"));
        state.update(newGame, Set.of("one"));
        state.release(oldGame);
        assertEquals(Team.OptionStatus.NEVER, original.getOption(Team.Option.NAME_TAG_VISIBILITY));
        state.release(newGame);
        assertEquals(
                Team.OptionStatus.FOR_OTHER_TEAMS,
                original.getOption(Team.Option.NAME_TAG_VISIBILITY));
        assertTrue(state.empty());
    }

    @Test
    void unassignedEntriesAreSharedUntilTheLastOwnerReleasesThem() {
        var board = new Board();
        var state = new LaserNameTags.BoardState(board.proxy);
        Object first = new Object();
        Object second = new Object();
        state.update(first, Set.of("one"));
        Team hidden = board.proxy.getEntryTeam("one");
        state.update(second, Set.of("one", "two"));
        assertSame(hidden, board.proxy.getEntryTeam("two"));
        state.release(first);
        assertSame(hidden, board.proxy.getEntryTeam("one"));
        state.release(second);
        assertNull(board.proxy.getEntryTeam("one"));
        assertTrue(board.teams.isEmpty());
    }

    @Test
    void scoreboardPluginReassignmentIsPreservedAndItsCurrentVisibilityRestored() {
        var board = new Board();
        var state = new LaserNameTags.BoardState(board.proxy);
        Object game = new Object();
        state.update(game, Set.of("one"));
        Team replacement = board.team("rank_009", Team.OptionStatus.FOR_OWN_TEAM, "one");
        state.update(game, Set.of("one"));
        assertSame(replacement, board.proxy.getEntryTeam("one"));
        assertEquals(
                Team.OptionStatus.NEVER, replacement.getOption(Team.Option.NAME_TAG_VISIBILITY));
        assertEquals(1, board.teams.size());
        state.release(game);
        assertEquals(
                Team.OptionStatus.FOR_OWN_TEAM,
                replacement.getOption(Team.Option.NAME_TAG_VISIBILITY));
        assertSame(replacement, board.proxy.getEntryTeam("one"));
    }

    @Test
    void recreatedTeamWithTheSameNameDoesNotInheritAStaleOriginalOption() {
        var board = new Board();
        Team old = board.team("rank_001", Team.OptionStatus.ALWAYS, "one");
        var state = new LaserNameTags.BoardState(board.proxy);
        Object game = new Object();
        state.update(game, Set.of("one"));
        old.unregister();
        Team replacement = board.team("rank_001", Team.OptionStatus.FOR_OTHER_TEAMS, "one");
        state.update(game, Set.of("one"));
        state.release(game);
        assertEquals(
                Team.OptionStatus.FOR_OTHER_TEAMS,
                replacement.getOption(Team.Option.NAME_TAG_VISIBILITY));
    }

    @Test
    void cleanupDoesNotOverwriteAThirdPartyVisibilityChange() {
        var board = new Board();
        Team original = board.team("rank_001", Team.OptionStatus.ALWAYS, "one");
        var state = new LaserNameTags.BoardState(board.proxy);
        Object game = new Object();
        state.update(game, Set.of("one"));
        original.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
        state.release(game);
        assertEquals(
                Team.OptionStatus.FOR_OWN_TEAM,
                original.getOption(Team.Option.NAME_TAG_VISIBILITY));
    }

    private static final class Board {
        final Map<String, Team> teams = new HashMap<>();
        final Map<String, Team> entries = new HashMap<>();
        final Scoreboard proxy =
                (Scoreboard)
                        Proxy.newProxyInstance(
                                Scoreboard.class.getClassLoader(),
                                new Class<?>[] {Scoreboard.class},
                                (object, method, args) ->
                                        switch (method.getName()) {
                                            case "getEntryTeam" -> entries.get((String) args[0]);
                                            case "getTeam" -> teams.get((String) args[0]);
                                            case "registerNewTeam" ->
                                                    team(
                                                            (String) args[0],
                                                            Team.OptionStatus.ALWAYS);
                                            case "hashCode" -> System.identityHashCode(object);
                                            case "equals" -> object == args[0];
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });

        Team team(String name, Team.OptionStatus visibility, String... members) {
            var model = new TeamModel(name, visibility);
            Team team = model.proxy;
            teams.put(name, team);
            for (String member : members) team.addEntry(member);
            return team;
        }

        private final class TeamModel {
            final String name;
            final Set<String> members = new HashSet<>();
            Team.OptionStatus visibility;
            boolean registered = true;
            final Team proxy;

            TeamModel(String name, Team.OptionStatus visibility) {
                this.name = name;
                this.visibility = visibility;
                proxy =
                        (Team)
                                Proxy.newProxyInstance(
                                        Team.class.getClassLoader(),
                                        new Class<?>[] {Team.class},
                                        (object, method, args) -> {
                                            switch (method.getName()) {
                                                case "getName":
                                                    return this.name;
                                                case "getScoreboard":
                                                    return registered ? Board.this.proxy : null;
                                                case "getOption":
                                                    return this.visibility;
                                                case "setOption":
                                                    this.visibility = (Team.OptionStatus) args[1];
                                                    return null;
                                                case "getPrefix":
                                                    return "prefix";
                                                case "getSuffix":
                                                    return "suffix";
                                                case "getEntries":
                                                    return Set.copyOf(members);
                                                case "addEntry":
                                                    {
                                                        String entry = (String) args[0];
                                                        Team previous = entries.get(entry);
                                                        if (previous != null && previous != object)
                                                            previous.removeEntry(entry);
                                                        members.add(entry);
                                                        entries.put(entry, (Team) object);
                                                        return null;
                                                    }
                                                case "removeEntry":
                                                    {
                                                        String entry = (String) args[0];
                                                        if (entries.get(entry) == object)
                                                            entries.remove(entry);
                                                        return members.remove(entry);
                                                    }
                                                case "unregister":
                                                    for (String entry : Set.copyOf(members))
                                                        ((Team) object).removeEntry(entry);
                                                    registered = false;
                                                    teams.remove(this.name, object);
                                                    return null;
                                                case "hashCode":
                                                    return System.identityHashCode(object);
                                                case "equals":
                                                    return object == args[0];
                                                default:
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                            }
                                        });
            }
        }
    }
}
