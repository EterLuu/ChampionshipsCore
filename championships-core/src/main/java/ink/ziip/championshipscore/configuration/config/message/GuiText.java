package ink.ziip.championshipscore.configuration.config.message;

/** Non-button presentation atoms shared by inventory renderers. */
public final class GuiText {
    public static final String SEPARATOR = " • ";
    public static final String VERSUS = "对阵";
    public static final String LEADER_MARK = "★ ";
    public static final String MEMBER_MARK = "• ";

    public static @org.jetbrains.annotations.NotNull String coordinate(int x, int y, int z) {
        return String.format(java.util.Locale.ROOT, "%d, %d, %d", x, y, z);
    }

    private GuiText() {
    }
}
