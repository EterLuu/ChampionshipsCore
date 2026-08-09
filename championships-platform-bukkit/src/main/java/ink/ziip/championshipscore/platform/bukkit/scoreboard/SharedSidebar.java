package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import fr.mrmicky.fastboard.adventure.FastBoard;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Component-native, packet-backed sidebar that never touches Bukkit's scoreboard API.
 *
 * <p>Folia deliberately disables that global API. Call {@link #show(Player)},
 * {@link #refresh(Player)} and {@link #hide(Player)} from the player's entity scheduler. Rendering
 * only replaces an immutable snapshot and is safe from a global coordinator thread.</p>
 */
public final class SharedSidebar {
    private static final int MAX_LINES = 15;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final Consumer<String> warning;
    private final AtomicBoolean warned = new AtomicBoolean();
    private volatile Component title;
    private volatile List<Component> lines = List.of();
    private volatile boolean disabled;

    public SharedSidebar(String objectiveName, Component title) {
        this(objectiveName, title, ignored -> { });
    }

    public SharedSidebar(String objectiveName, Component title, Consumer<String> warning) {
        // objectiveName remains in the API so callers can keep a stable logical identifier. The
        // packet library allocates a collision-safe per-viewer objective internally.
        if (objectiveName == null || objectiveName.isBlank()) {
            throw new IllegalArgumentException("objectiveName must not be blank");
        }
        this.title = java.util.Objects.requireNonNull(title, "title");
        this.warning = java.util.Objects.requireNonNull(warning, "warning");
    }

    public void render(Component title, List<Component> requestedLines) {
        this.title = java.util.Objects.requireNonNull(title, "title");
        this.lines = normalize(requestedLines);
    }

    /**
     * Renders a viewer-specific snapshot without changing the defaults used for newly shown boards.
     * The caller must invoke this from that player's entity scheduler.
     */
    public void render(Player player, Component title, List<Component> requestedLines) {
        if (disabled) return;
        Viewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null || viewer.player() != player) return;
        try {
            update(viewer.board(), java.util.Objects.requireNonNull(title, "title"), normalize(requestedLines));
        } catch (RuntimeException | LinkageError failure) {
            viewers.remove(player.getUniqueId(), viewer);
            disable(failure);
        }
    }

    public void show(Player player) {
        if (disabled) return;
        Viewer existing = viewers.get(player.getUniqueId());
        if (existing != null && existing.player() == player) {
            update(existing.board());
            return;
        }
        if (existing != null) delete(existing.board());
        try {
            FastBoard board = new FastBoard(player);
            viewers.put(player.getUniqueId(), new Viewer(player, board));
            update(board);
        } catch (RuntimeException | LinkageError failure) {
            viewers.remove(player.getUniqueId());
            disable(failure);
        }
    }

    public void refresh(Player player) {
        if (disabled) return;
        Viewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null || viewer.player() != player) return;
        try {
            update(viewer.board());
        } catch (RuntimeException | LinkageError failure) {
            viewers.remove(player.getUniqueId(), viewer);
            disable(failure);
        }
    }

    public void hide(Player player) {
        Viewer viewer = viewers.remove(player.getUniqueId());
        if (viewer != null) delete(viewer.board());
    }

    /** Deletes every client-side objective owned by this sidebar. Safe to call during plugin shutdown. */
    public void hideAll() {
        List<Viewer> snapshot = List.copyOf(viewers.values());
        viewers.clear();
        for (Viewer viewer : snapshot) delete(viewer.board());
    }

    public boolean isShown(Player player) {
        Viewer viewer = viewers.get(player.getUniqueId());
        return viewer != null && viewer.player() == player;
    }

    private void update(FastBoard board) {
        update(board, title, lines);
    }

    private static void update(FastBoard board, Component title, List<Component> lines) {
        board.updateTitle(title);
        board.updateLines(lines);
    }

    private static List<Component> normalize(List<Component> requestedLines) {
        List<Component> snapshot = List.copyOf(requestedLines);
        return snapshot.size() <= MAX_LINES ? snapshot : List.copyOf(snapshot.subList(0, MAX_LINES));
    }

    private void delete(FastBoard board) {
        try {
            if (!board.isDeleted()) board.delete();
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
        }
    }

    private void disable(Throwable failure) {
        disabled = true;
        hideAll();
        warn(failure);
    }

    private void warn(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            warning.accept("Packet sidebar disabled without affecting the match: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private record Viewer(Player player, FastBoard board) {
    }
}
