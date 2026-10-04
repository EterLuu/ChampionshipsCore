package ink.ziip.championshipscore.platform.bukkit.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.permissions.Permissible;

/** Shared permission check and legacy-code parsing for public chat on Core and game workers. */
public final class ChatMessageText {
    private ChatMessageText() {}

    public static Component format(Permissible sender, Component message) {
        if (!sender.hasPermission("cc.admin") && !sender.hasPermission("cc.refuge")) return message;
        return LegacyText.component(
                "&f" + PlainTextComponentSerializer.plainText().serialize(message));
    }
}
