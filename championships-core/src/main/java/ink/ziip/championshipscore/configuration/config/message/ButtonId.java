package ink.ziip.championshipscore.configuration.config.message;

import org.jetbrains.annotations.NotNull;

/** Stable identifiers for GUI buttons shared by every structured menu. */
public enum ButtonId {
    PREVIOUS("previous"),
    NEXT("next"),
    PAGE("page"),
    BACK("back"),
    CLOSE("close"),
    REFRESH("refresh"),
    CONFIRM("confirm"),
    CANCEL("cancel"),
    BORDER("border"),
    EMPTY("empty");

    private final String id;

    ButtonId(@NotNull String id) {
        this.id = id;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String path() {
        return "buttons." + id;
    }
}
