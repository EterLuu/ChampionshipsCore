package ink.ziip.championshipscore.api.finale;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinaleGameRegistryTest {
    @Test
    void dodgeboltAndDragonEggCarnivalAreRegisteredFinaleGames() {
        assertTrue(FinaleGameRegistry.isRegistered(GameTypeEnum.Dodgebolt));
        assertTrue(FinaleGameRegistry.isRegistered(GameTypeEnum.DragonEggCarnival));
        assertFalse(FinaleGameRegistry.isRegistered(GameTypeEnum.AceRace));
    }

    @Test
    void parsesCanonicalAndDashedGameNamesCaseInsensitively() {
        FinaleGameDefinition dragonEgg = FinaleGameRegistry.parse("Dragon-Egg-Carnival");
        assertNotNull(dragonEgg);
        assertEquals(GameTypeEnum.DragonEggCarnival, dragonEgg.gameType());
        assertEquals(GameTypeEnum.Dodgebolt, FinaleGameRegistry.parse("DODGEBOLT").gameType());
    }
}
