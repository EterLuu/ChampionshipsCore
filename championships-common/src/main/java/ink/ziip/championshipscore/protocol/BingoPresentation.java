package ink.ziip.championshipscore.protocol;

import java.util.Map;

/**
 * Immutable, Core-owned presentation snapshot for one match. Values intentionally remain legacy
 * text templates: the worker resolves placeholders first and only then creates Adventure components.
 */
public record BingoPresentation(Map<String, String> messages) {
    public BingoPresentation {
        messages = ProtocolSupport.immutableAttributes(messages);
    }

    public String message(String key) {
        String value = messages.get(key);
        if (value == null) throw new IllegalArgumentException("Missing Bingo presentation key " + key);
        return value;
    }
}
