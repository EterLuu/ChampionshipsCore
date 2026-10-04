package ink.ziip.championshipscore.api.game.buildmart.mechanics;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Renders the existing map image and exactly one cursor, separately for each viewer. */
public final class BuildMartSelfMapRenderer extends MapRenderer {
    private final List<MapRenderer> backgrounds;

    private BuildMartSelfMapRenderer(List<MapRenderer> backgrounds) {
        super(true);
        this.backgrounds = backgrounds;
    }

    public static void attach(MapView view, World world) {
        // Saved map 183 predates the renamed/cloned Build Mart world; its old world can be absent.
        view.setWorld(world);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        if (view.getRenderers().stream().anyMatch(BuildMartSelfMapRenderer.class::isInstance))
            return;
        List<MapRenderer> backgrounds = List.copyOf(view.getRenderers());
        backgrounds.forEach(view::removeRenderer);
        view.addRenderer(new BuildMartSelfMapRenderer(backgrounds));
    }

    public static void detach(MapView view) {
        for (MapRenderer renderer : List.copyOf(view.getRenderers())) {
            if (renderer instanceof BuildMartSelfMapRenderer self) {
                view.removeRenderer(self);
                self.backgrounds.forEach(view::addRenderer);
            }
        }
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        // Bukkit merges cursors from separate renderer layers. Render the image in this same layer
        // before replacing its cursors, so stale vanilla/player decorations cannot leak through.
        for (MapRenderer background : backgrounds) background.render(view, canvas, player);
        MapCursorCollection cursors = new MapCursorCollection();
        if (view.getWorld() != null && view.getWorld().equals(player.getWorld())) {
            int scale = 1 << view.getScale().getValue();
            double x = (player.getX() - view.getCenterX()) / scale;
            double z = (player.getZ() - view.getCenterZ()) / scale;
            byte cursorX = (byte) Math.clamp(Math.round(x * 2), -128, 127);
            byte cursorZ = (byte) Math.clamp(Math.round(z * 2), -128, 127);
            byte rotation = (byte) Math.floorMod(Math.round(player.getYaw() * 16.0F / 360.0F), 16);
            MapCursor.Type type =
                    Math.abs(x) <= 63 && Math.abs(z) <= 63
                            ? MapCursor.Type.PLAYER
                            : MapCursor.Type.PLAYER_OFF_MAP;
            cursors.addCursor(new MapCursor(cursorX, cursorZ, rotation, type, true));
        }
        canvas.setCursors(cursors);
    }
}
