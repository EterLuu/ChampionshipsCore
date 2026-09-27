package ink.ziip.championshipscore.api.object.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameTypeCommandTest {
    @Test
    void parsesCanonicalNamesCaseInsensitively() {
        assertEquals(GameTypeEnum.BattleBox, GameTypeEnum.fromCommand("battle-box"));
        assertEquals(GameTypeEnum.ParkourWarrior, GameTypeEnum.fromCommand("PARKOUR_WARRIOR"));
        assertEquals(GameTypeEnum.SnowballShowdown, GameTypeEnum.fromCommand("snowball"));
        assertEquals(GameTypeEnum.SnowballShowdown, GameTypeEnum.fromCommand("SnowballShowdown"));
        assertEquals(GameTypeEnum.RiptideRush, GameTypeEnum.fromCommand("riptide"));
        assertEquals(GameTypeEnum.RiptideRush, GameTypeEnum.fromCommand("raft"));
        assertEquals(GameTypeEnum.RiptideRush, GameTypeEnum.fromCommand("RaftSurvival"));
        assertEquals(GameTypeEnum.RiptideRush, GameTypeEnum.fromCommand("riptide-rush"));
        assertNull(GameTypeEnum.fromCommand("not-a-game"));
    }

    @Test
    void exposesTheSameTokensUsedByStartCommands() {
        assertEquals("snowball", GameTypeEnum.SnowballShowdown.commandName());
        assertEquals("acerace", GameTypeEnum.AceRace.commandName());
        assertEquals("dragoneggcarnival", GameTypeEnum.DragonEggCarnival.commandName());
        assertEquals("riptide", GameTypeEnum.RiptideRush.commandName());
    }
}
