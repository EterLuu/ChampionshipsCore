package ink.ziip.championshipscore.api.game.acerace.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ConfigurationStateExtension.class)
class AceRaceEquipmentTest {

    @Test
    void parsesSegmentEquipment() {
        assertEquals(
                AceRaceEquipment.DOLPHINS_GRACE, AceRaceEquipment.fromConfig("dolphins_grace"));
        assertEquals("海豚的恩惠", AceRaceEquipment.DOLPHINS_GRACE.displayName());
    }

    @Test
    void labelsFollowReloadWithoutChangingThePersistedEquipmentValue() throws Exception {
        AceRaceEquipment.ELYTRA.displayName();
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var core =
                (ChampionshipsCore)
                        ((sun.misc.Unsafe) field.get(null))
                                .allocateInstance(ChampionshipsCore.class);
        var config = new MessageConfig(core);
        var document =
                YamlConfiguration.loadConfiguration(
                        java.nio.file.Path.of("src/main/resources/message.yml").toFile());
        document.set("code-text.ace-race-equipment.elytra-1", "Reloaded equipment");
        config.loadFromConfiguration(document);
        assertEquals("Reloaded equipment", AceRaceEquipment.ELYTRA.displayName());
        assertEquals("elytra", AceRaceEquipment.ELYTRA.configValue());
        document.set("code-text", null);
        config.loadFromConfiguration(document);
        assertEquals("鞘翅", AceRaceEquipment.ELYTRA.displayName());
    }
}
