package ink.ziip.championshipscore.api.game.buildmart.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class BuildMartSelfMapRendererTest {
    @Test
    void bindsSavedMapToCurrentWorldAndInstallsOneContextualRenderer() {
        View fixture = new View();
        World arena = world();
        BuildMartSelfMapRenderer.attach(fixture.view, arena);
        BuildMartSelfMapRenderer.attach(fixture.view, arena);
        assertSame(arena, fixture.world);
        assertFalse(fixture.tracking);
        assertFalse(fixture.unlimited);
        assertEquals(1, fixture.renderers.size());
        assertTrue(fixture.renderers.getFirst().isContextual());
    }

    @Test
    void cleanupRestoresOriginalImageRenderersAndCanRunRepeatedly() {
        View fixture = new View();
        List<MapRenderer> originals = List.copyOf(fixture.renderers);
        BuildMartSelfMapRenderer.attach(fixture.view, world());
        BuildMartSelfMapRenderer.detach(fixture.view);
        BuildMartSelfMapRenderer.detach(fixture.view);
        assertEquals(originals, fixture.renderers);
    }

    @Test
    void viewingFromAnotherWorldPreservesImageAndReplacesPreviousCursors() {
        View fixture = new View();
        BuildMartSelfMapRenderer.attach(fixture.view, world());
        Player viewer =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getWorld")) return null;
                                    throw new UnsupportedOperationException(method.getName());
                                });
        MapCursorCollection old = new MapCursorCollection();
        MapCursorCollection[] cursors = {old};
        byte[] image = {0};
        MapCanvas canvas =
                (MapCanvas)
                        Proxy.newProxyInstance(
                                MapCanvas.class.getClassLoader(),
                                new Class<?>[] {MapCanvas.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "setPixel" -> {
                                                image[0] = (byte) args[2];
                                                yield null;
                                            }
                                            case "setCursors" -> {
                                                cursors[0] = (MapCursorCollection) args[0];
                                                yield null;
                                            }
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        fixture.renderers.getFirst().render(fixture.view, canvas, viewer);
        assertEquals(17, image[0]);
        assertNotSame(old, cursors[0]);
        assertEquals(0, cursors[0].size());
    }

    private static World world() {
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("equals")) return proxy == args[0];
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    private static final class View {
        World world;
        boolean tracking = true;
        boolean unlimited = true;
        final List<MapRenderer> renderers =
                new ArrayList<>(
                        List.of(
                                new MapRenderer() {
                                    @Override
                                    public void render(
                                            MapView view, MapCanvas canvas, Player player) {
                                        canvas.setPixel(1, 2, (byte) 17);
                                    }
                                }));
        final MapView view =
                (MapView)
                        Proxy.newProxyInstance(
                                MapView.class.getClassLoader(),
                                new Class<?>[] {MapView.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "setWorld" -> {
                                                world = (World) args[0];
                                                yield null;
                                            }
                                            case "getWorld" -> world;
                                            case "setTrackingPosition" -> {
                                                tracking = (boolean) args[0];
                                                yield null;
                                            }
                                            case "setUnlimitedTracking" -> {
                                                unlimited = (boolean) args[0];
                                                yield null;
                                            }
                                            case "getRenderers" -> List.copyOf(renderers);
                                            case "removeRenderer" -> renderers.remove(args[0]);
                                            case "addRenderer" -> {
                                                renderers.add((MapRenderer) args[0]);
                                                yield null;
                                            }
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
    }
}
