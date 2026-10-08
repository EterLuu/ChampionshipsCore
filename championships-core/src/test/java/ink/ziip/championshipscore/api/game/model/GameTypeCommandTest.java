package ink.ziip.championshipscore.api.game.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GameTypeCommandTest {
    @Test
    void parsesCanonicalNamesCaseInsensitively() {
        assertEquals(GameTypeEnum.BattleBox, GameTypeEnum.fromCommand("battle-box"));
        assertEquals(GameTypeEnum.ParkourWarrior, GameTypeEnum.fromCommand("PARKOUR_WARRIOR"));
        assertEquals(GameTypeEnum.LaserShowdown, GameTypeEnum.fromCommand("lasershowdown"));
        assertEquals(GameTypeEnum.LaserShowdown, GameTypeEnum.fromCommand("LaserShowdown"));
        assertNull(GameTypeEnum.fromCommand("snowball"));
        assertNull(GameTypeEnum.fromCommand("SnowballShowdown"));
        assertEquals(GameTypeEnum.RiptideRush, GameTypeEnum.fromCommand("riptide"));
        assertNull(GameTypeEnum.fromCommand("raft"));
        assertNull(GameTypeEnum.fromCommand("RaftSurvival"));
        assertEquals(GameTypeEnum.RiptideRush, GameTypeEnum.fromCommand("riptide-rush"));
        assertNull(GameTypeEnum.fromCommand("not-a-game"));
    }

    @Test
    void exposesTheSameTokensUsedByStartCommands() {
        assertEquals("lasershowdown", GameTypeEnum.LaserShowdown.commandName());
        assertEquals("acerace", GameTypeEnum.AceRace.commandName());
        assertEquals("dragoneggcarnival", GameTypeEnum.DragonEggCarnival.commandName());
        assertEquals("riptide", GameTypeEnum.RiptideRush.commandName());
    }
}
