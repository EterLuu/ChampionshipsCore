package ink.ziip.championshipscore.api.game.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameTypeEnumTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            nullValues = "null",
            value = {
                "battle-box, BattleBox",
                "PARKOUR_WARRIOR, ParkourWarrior",
                "lasershowdown, LaserShowdown",
                "LaserShowdown, LaserShowdown",
                "riptide, RiptideRush",
                "riptide-rush, RiptideRush",
                // Removed games and their old aliases must not resolve to a current game.
                "snowball, null",
                "SnowballShowdown, null",
                "raft, null",
                "RaftSurvival, null",
                "not-a-game, null"
            })
    void parsesCanonicalNamesCaseInsensitively(String command, GameTypeEnum expected) {
        assertEquals(expected, GameTypeEnum.fromCommand(command), command);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "LaserShowdown, lasershowdown",
        "AceRace, acerace",
        "DragonEggCarnival, dragoneggcarnival",
        "RiptideRush, riptide"
    })
    void exposesTheSameTokensUsedByStartCommands(GameTypeEnum game, String token) {
        assertEquals(token, game.commandName());
    }
}
