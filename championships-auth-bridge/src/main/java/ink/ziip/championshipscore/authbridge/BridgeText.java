package ink.ziip.championshipscore.authbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Parses the same &#RRGGBB and legacy formatting syntax used by Core resources. */
public final class BridgeText {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private BridgeText() {
    }

    public static Component component(String text) {
        return SERIALIZER.deserialize(text == null ? "" : text);
    }
}
