package ink.ziip.championshipscore.configuration.config.message;

/** Non-button presentation atoms shared by inventory renderers. */
public final class GuiText {
    public static net.kyori.adventure.text.Component row(String path, String text) {
        return ink.ziip.championshipscore.platform.bukkit.text.ComponentTemplates.render(
                GuiConfig.text(path),
                java.util.Map.of(
                        "%message%",
                        ink.ziip.championshipscore.platform.bukkit.text.LegacyText.component(
                                text)));
    }

    public static String versus() {
        return MessageConfig.codeText("gui-text.versus-1");
    }

    public static @org.jetbrains.annotations.NotNull String coordinate(int x, int y, int z) {
        return MessageConfig.codeText("gui-text.coordinate", x, y, z);
    }

    private GuiText() {}
}
