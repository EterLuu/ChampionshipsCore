package ink.ziip.championshipscore.api.game.area.prepare;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * One configurable step in a game's prepare flow (e.g. "set spectator spawn", "stamp 4 copies", "add an
 * escapee spawn"). Steps are built per-area by a {@link PrepareFlowDefinition} and bound to that area
 * instance. The session is passed to every method so session-only state (world-confirmed, stamped) can be
 * read/written, and so steps can reach {@code plugin} via {@link PrepareSession#getPlugin()}.
 *
 * <p>Only the method matching {@link #captureType()} is ever called by the listener; the others default to
 * {@code null}/{@code false}.
 */
public abstract class PrepareStep {
    private final String key;
    private final Component displayName;
    private final Component description;
    private final Material icon;
    private final StepCaptureType captureType;

    protected PrepareStep(@NotNull String key, @NotNull Component displayName, @NotNull Component description,
                          @NotNull Material icon, @NotNull StepCaptureType captureType) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.captureType = captureType;
    }

    public @NotNull String key() {
        return key;
    }

    public @NotNull Component displayName() {
        return displayName;
    }

    public @NotNull Component description() {
        return description;
    }

    public @NotNull Material icon() {
        return icon;
    }

    public @NotNull StepCaptureType captureType() {
        return captureType;
    }

    /** Whether this step needs the WorldEdit selection wand supplied by prepare mode. */
    public boolean requiresWorldEdit() {
        return captureType == StepCaptureType.SCHEMATIC || captureType == StepCaptureType.WE_SELECTION;
    }

    /**
     * Whether this step is already satisfied. {@code session} may be {@code null} when previewing an area
     * in the list GUI (before entering prepare mode); session-only steps should return {@code false} then.
     */
    public abstract boolean isSet(PrepareSession session);

    /**
     * Capture for {@link StepCaptureType#CONFIRM_WORLD}/{@link StepCaptureType#STAND_AND_RUN}/
     * {@link StepCaptureType#WE_SELECTION}/{@link StepCaptureType#SCHEMATIC}. Returns a feedback message
     * (legacy {@code §} codes allowed), or {@code null} for no message.
     */
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        return null;
    }

    /** Capture for {@link StepCaptureType#STAMP} after the count is read from an anvil. */
    public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        return null;
    }

    /** Add the player's current location for a {@link StepCaptureType#LIST} step. */
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        return null;
    }

    /** Clear a {@link StepCaptureType#LIST} step. */
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        return null;
    }

    /** Current entry count of a {@link StepCaptureType#LIST} step (for display). */
    public int listCount(@NotNull PrepareSession session) {
        return 0;
    }

    /** Label shown by the list editor's add button. Custom list steps can describe non-location data. */
    public @NotNull Component listAddLabel() {
        return Component.text("添加当前点位");
    }

    /** Hint shown below the list editor's add button. */
    public @NotNull Component listAddHint() {
        return Component.text("站到目标位置后点击");
    }
}
