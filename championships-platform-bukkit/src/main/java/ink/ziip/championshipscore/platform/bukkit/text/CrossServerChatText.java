package ink.ziip.championshipscore.platform.bukkit.text;

import ink.ziip.championshipscore.protocol.CrossServerChatMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Objects;
import java.util.UUID;

/** Adventure serialization boundary for the shared cross-server chat protocol. */
public final class CrossServerChatText {
    private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

    private CrossServerChatText() {
    }

    public static CrossServerChatMessage message(String sourceInstance, UUID senderId, String senderName,
                                                  PlayerPresentation presentation, Component content,
                                                  long createdAt) {
        Objects.requireNonNull(presentation, "presentation");
        return new CrossServerChatMessage(UUID.randomUUID(), sourceInstance, senderId, senderName,
                presentation.label(), presentation.teamColorCode(), presentation.activePlayer(),
                SERIALIZER.serialize(Objects.requireNonNull(content, "content")), createdAt);
    }

    public static Component render(CrossServerChatMessage message) {
        Objects.requireNonNull(message, "message");
        Component content = SERIALIZER.deserialize(message.messageJson());
        return new PlayerPresentation(message.label(), message.teamColorCode(), message.activePlayer())
                .chatLine(message.senderName(), content);
    }
}
