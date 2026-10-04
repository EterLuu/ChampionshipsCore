package ink.ziip.championshipscore.api.game.start;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;

import java.util.List;

class GameStartArgumentsTest {
    @Test
    void allGamesUseMapFirstWithAllOrExplicitTeams() {
        for (GameTypeEnum game : GameTypeEnum.values()) {
            var all =
                    GameStartArguments.parse(
                            new String[] {game.commandName(), "map", "all"}, false);
            assertEquals(game, all.game());
            assertEquals("map", all.map());
            assertTrue(all.allTeams());
            var selected =
                    GameStartArguments.parse(
                            new String[] {game.commandName(), "map", "#1", "蓝队"}, false);
            assertEquals(List.of("#1", "蓝队"), selected.teams());
        }
    }

    @Test
    void anEventWithoutOverridesRetainsAutomaticMapAndTeamSelection() {
        var args = GameStartArguments.parse(new String[] {"battlebox"}, true);
        assertNull(args.map());
        assertTrue(args.allTeams());
        assertTrue(args.arenas().automatic());
        assertThrows(
                IllegalArgumentException.class,
                () -> GameStartArguments.parse(new String[] {"battlebox"}, false));
    }

    @Test
    void arenaNumbersBecomeRuntimeIndicesAndPreserveTheSelectedOrder() {
        var args =
                GameStartArguments.parse(
                        new String[] {"tntrun", "map", "all", "--arena", "3,1"}, false);
        assertEquals(List.of(2, 0), args.arenas().resolve(4));
        assertEquals(List.of(0, 1, 2, 3), ArenaSelection.all().resolve(4));
        assertThrows(IllegalArgumentException.class, () -> args.arenas().resolve(2));
    }

    @Test
    void quotedMapAndTeamNamesAreNotSplitIntoExtraParticipants() {
        var args =
                GameStartArguments.parse(
                        new String[] {
                            "laserbox",
                            "\"Test",
                            "Map\"",
                            "'Red",
                            "Fox'",
                            "'Blue",
                            "Fox'",
                            "--arena=2"
                        },
                        false);
        assertEquals("Test Map", args.map());
        assertEquals(List.of("Red Fox", "Blue Fox"), args.teams());
        assertEquals(List.of(1), args.arenas().resolve(3));
    }

    @Test
    void invalidSelectorsFailInsteadOfSilentlyDroppingTeamsOrArenas() {
        for (String[] args :
                List.of(
                        new String[] {"unknown", "map", "all"},
                        new String[] {"battlebox", "map", "all", "red"},
                        new String[] {"battlebox", "map", "red", "RED"},
                        new String[] {"battlebox", "map", "--arena"},
                        new String[] {"battlebox", "map", "--arena=1", "--arena=2"},
                        new String[] {"battlebox", "map", "--unsupported"},
                        new String[] {"battlebox", "\"open", "map"})) {
            assertThrows(
                    IllegalArgumentException.class, () -> GameStartArguments.parse(args, false));
        }
        for (String value : List.of("0", "-1", "1,1", "1,", "1.5", "2147483648")) {
            assertThrows(IllegalArgumentException.class, () -> ArenaSelection.parse(value));
        }
    }

    @Test
    void everyGameHasAStartTopologyIncludingAllFinals() {
        for (GameTypeEnum game : GameTypeEnum.values())
            assertNotNull(GameStartRules.topology(game));
        for (GameTypeEnum game :
                List.of(GameTypeEnum.BattleBox, GameTypeEnum.ParkourTag, GameTypeEnum.LaserBox)) {
            GameStartRules.validateTeamCount(game, 6);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> GameStartRules.validateTeamCount(game, 3));
        }
        for (GameTypeEnum game :
                List.of(
                        GameTypeEnum.Dodgebolt,
                        GameTypeEnum.SulfurSoccer,
                        GameTypeEnum.DragonEggCarnival)) {
            GameStartRules.validateTeamCount(game, 2);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> GameStartRules.validateTeamCount(game, 4));
        }
    }
}
