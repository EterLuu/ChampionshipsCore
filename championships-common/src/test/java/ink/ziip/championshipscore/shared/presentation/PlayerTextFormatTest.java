package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.BingoPresentation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class PlayerTextFormatTest {
    @Test
    void absentFieldsUseLegacyResourcesWithoutMutatingOldManifest() {
        var old = new BingoPresentation(Map.of("game.name", "Bingo"));
        assertEquals(PlayerTextFormat.legacy(), PlayerTextFormat.from(old.messages()));
        assertEquals(Map.of("game.name", "Bingo"), old.messages());
        assertEquals("&8[%label%&8]&r ", PlayerTextFormat.legacy().prefix());
    }

    @Test
    void frozenFormatsRetainEmptyValuesAndRejectPartialOrUnknownSchemas() {
        var empty = new PlayerTextFormat("", "", "", "", "", "");
        var values = new HashMap<>(empty.attributes());
        assertEquals(empty, PlayerTextFormat.from(values));
        values.remove(PlayerTextFormat.PREFIX + "chat");
        assertThrows(IllegalArgumentException.class, () -> PlayerTextFormat.from(values));
        values.put(PlayerTextFormat.PREFIX + "chat", "custom");
        values.put(PlayerTextFormat.PREFIX + "version", "2");
        assertThrows(IllegalArgumentException.class, () -> PlayerTextFormat.from(values));
        assertThrows(UnsupportedOperationException.class, () -> empty.attributes().clear());
    }
}
