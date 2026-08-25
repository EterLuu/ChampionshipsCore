package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormalEventMapRenameTest {
    @Test
    void updatesEveryCaseInsensitiveFormalEventReferenceForTheRenamedGame() throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString("""
                formal-events:
                  TGTTOS:
                    maps: [cod, industry, COD]
                  SkyWars:
                    maps: [area2]
                """);

        assertTrue(FormalEventMapRename.replaceRegistrations(configuration, GameTypeEnum.TGTTOS,
                "CoD", "coastal"));
        assertEquals(List.of("coastal", "industry", "coastal"),
                configuration.getStringList("formal-events.TGTTOS.maps"));
        assertEquals(List.of("area2"), configuration.getStringList("formal-events.SkyWars.maps"));
        assertFalse(FormalEventMapRename.replaceRegistrations(configuration, GameTypeEnum.TGTTOS,
                "missing", "unused"));
    }
}
