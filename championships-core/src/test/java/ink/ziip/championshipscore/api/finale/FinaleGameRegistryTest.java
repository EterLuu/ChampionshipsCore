package ink.ziip.championshipscore.api.finale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;

class FinaleGameRegistryTest {
    @Test
    void dodgeboltAndDragonEggCarnivalAreRegisteredFinaleGames() {
        assertTrue(FinaleGameRegistry.isRegistered(GameTypeEnum.Dodgebolt));
        assertTrue(FinaleGameRegistry.isRegistered(GameTypeEnum.DragonEggCarnival));
        assertTrue(FinaleGameRegistry.isRegistered(GameTypeEnum.SulfurSoccer));
        assertFalse(FinaleGameRegistry.isRegistered(GameTypeEnum.AceRace));
    }

    @Test
    void parsesCanonicalAndDashedGameNamesCaseInsensitively() {
        FinaleGameDefinition dragonEgg = FinaleGameRegistry.parse("Dragon-Egg-Carnival");
        assertNotNull(dragonEgg);
        assertEquals(GameTypeEnum.DragonEggCarnival, dragonEgg.gameType());
        assertEquals(GameTypeEnum.Dodgebolt, FinaleGameRegistry.parse("DODGEBOLT").gameType());
        assertEquals(
                GameTypeEnum.SulfurSoccer, FinaleGameRegistry.parse("Sulfur-Soccer").gameType());
        assertFalse(FinaleGameRegistry.parse("sulfursoccer").supportsPartialRoster());
    }
}
