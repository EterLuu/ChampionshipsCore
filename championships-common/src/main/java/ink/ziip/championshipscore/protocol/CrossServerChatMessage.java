package ink.ziip.championshipscore.protocol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Trusted Redis payload for one global chat line shared by Core and game workers. */
public record CrossServerChatMessage(
        UUID messageId,
        String sourceInstance,
        UUID senderId,
        String senderName,
        String label,
        String teamColorCode,
        boolean activePlayer,
        String messageJson,
        long createdAt,
        boolean daily,
        String renderedJson) {
    public CrossServerChatMessage(
            UUID messageId,
            String sourceInstance,
            UUID senderId,
            String senderName,
            String label,
            String teamColorCode,
            boolean activePlayer,
            String messageJson,
            long createdAt) {
        this(
                messageId,
                sourceInstance,
                senderId,
                senderName,
                label,
                teamColorCode,
                activePlayer,
                messageJson,
                createdAt,
                false);
    }

    /** Older senders carry metadata/content only; current senders also freeze the rendered line. */
    public CrossServerChatMessage(
            UUID messageId,
            String sourceInstance,
            UUID senderId,
            String senderName,
            String label,
            String teamColorCode,
            boolean activePlayer,
            String messageJson,
            long createdAt,
            boolean daily) {
        this(
                messageId,
                sourceInstance,
                senderId,
                senderName,
                label,
                teamColorCode,
                activePlayer,
                messageJson,
                createdAt,
                daily,
                null);
    }

    public CrossServerChatMessage {
        Objects.requireNonNull(messageId, "messageId");
        sourceInstance = text(sourceInstance, "sourceInstance", 128);
        Objects.requireNonNull(senderId, "senderId");
        senderName = text(senderName, "senderName", 64);
        Objects.requireNonNull(label, "label");
        if (label.length() > 256) throw new IllegalArgumentException("label is too long");
        teamColorCode = optionalText(teamColorCode, "teamColorCode", 16);
        messageJson = text(messageJson, "messageJson", 8_192);
        if (renderedJson != null) renderedJson = text(renderedJson, "renderedJson", 32_768);
        if (createdAt < 1) throw new IllegalArgumentException("createdAt must be positive");
    }

    public Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("messageId", messageId.toString());
        fields.put("sourceInstance", sourceInstance);
        fields.put("senderId", senderId.toString());
        fields.put("senderName", senderName);
        fields.put("label", label);
        fields.put("teamColorCode", teamColorCode == null ? "" : teamColorCode);
        fields.put("activePlayer", Boolean.toString(activePlayer));
        fields.put("daily", Boolean.toString(daily));
        fields.put("messageJson", messageJson);
        if (renderedJson != null) fields.put("renderedJson", renderedJson);
        fields.put("createdAt", Long.toString(createdAt));
        return Map.copyOf(fields);
    }

    public static CrossServerChatMessage parse(Map<String, String> fields) {
        Objects.requireNonNull(fields, "fields");
        String active = required(fields, "activePlayer");
        if (!active.equals("true") && !active.equals("false")) {
            throw new IllegalArgumentException("activePlayer must be true or false");
        }
        String color = fields.get("teamColorCode");
        String label = fields.get("label");
        if (label == null) throw new IllegalArgumentException("Missing field label");
        String daily = fields.getOrDefault("daily", "false");
        if (!daily.equals("true") && !daily.equals("false")) {
            throw new IllegalArgumentException("daily must be true or false");
        }
        return new CrossServerChatMessage(
                UUID.fromString(required(fields, "messageId")),
                required(fields, "sourceInstance"),
                UUID.fromString(required(fields, "senderId")),
                required(fields, "senderName"),
                label,
                color == null || color.isBlank() ? null : color,
                Boolean.parseBoolean(active),
                required(fields, "messageJson"),
                Long.parseLong(required(fields, "createdAt")),
                Boolean.parseBoolean(daily),
                fields.get("renderedJson"));
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing field " + key);
        return value;
    }

    private static String text(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        if (value.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return value;
    }

    private static String optionalText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return value;
    }
}
