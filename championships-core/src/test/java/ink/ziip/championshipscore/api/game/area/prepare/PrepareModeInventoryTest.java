package ink.ziip.championshipscore.api.game.area.prepare;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.List;

@ExtendWith(ConfigurationStateExtension.class)
class PrepareModeInventoryTest {
    private static final String PATH = "map-editor.menus.prepare-toolbar";

    @Test
    void refreshAndCreativeProtectionKeepSessionSlotsAcrossReload() throws Exception {
        var document = new YamlConfiguration();
        document.set(PATH + ".items.status.slot", 7);
        activate(document);
        var session = allocate(PrepareSession.class);
        set(session, "hotbarLayout", PrepareModeInventory.layout());
        set(session, "steps", List.of());
        assertTrue(PrepareModeInventory.isControlSlot(session, 7));
        assertFalse(PrepareModeInventory.isControlSlot(session, 0));
        assertFalse(PrepareModeInventory.isControlSlot(session, 5));

        document.set(PATH + ".items.status.slot", 0);
        activate(document);
        assertEquals(7, session.getHotbarLayout().control("status"));
        assertTrue(PrepareModeInventory.isControlSlot(session, 7));
        assertFalse(PrepareModeInventory.isControlSlot(session, 0));
        assertEquals(0, PrepareModeInventory.layout().control("status"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> session.getHotbarLayout().controls().put("status", 0));

        var selection =
                new PrepareStep(
                        "selection",
                        Component.empty(),
                        Component.empty(),
                        Material.WOODEN_AXE,
                        StepCaptureType.WE_SELECTION) {
                    @Override
                    public boolean isSet(PrepareSession ignored) {
                        return false;
                    }
                };
        set(session, "steps", List.of(selection));
        assertTrue(PrepareModeInventory.isControlSlot(session, 5));
        for (int slot : List.of(-1, 9, 36, 40))
            assertFalse(PrepareModeInventory.isControlSlot(session, slot));
    }

    private static void activate(YamlConfiguration document) throws Exception {
        Field field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, document);
    }
}
