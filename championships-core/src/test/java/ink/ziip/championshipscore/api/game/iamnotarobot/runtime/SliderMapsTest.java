package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderArt;

import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

class SliderMapsTest {
    @Test
    void onlyNewContentWritesToTheSharedCanvasAndUsesViewerOrder() {
        byte[] image = new byte[SliderArt.WIDTH * SliderArt.HEIGHT];
        int tile = 102;
        int base = tile / 16 * 128 * SliderArt.WIDTH + tile % 16 * 128;
        image[base + 63 * SliderArt.WIDTH + 37] = 98;
        var writes = new AtomicInteger();
        byte[][] pixels = new byte[128][128];
        var canvas =
                (MapCanvas)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {MapCanvas.class},
                                (self, method, args) -> {
                                    assertEquals("setPixel", method.getName());
                                    pixels[(int) args[1]][(int) args[0]] = (byte) args[2];
                                    writes.incrementAndGet();
                                    return null;
                                });
        var renderer = new SliderMaps.TileRenderer();
        assertFalse(renderer.isContextual());
        renderer.set(image, tile);
        renderer.render(null, canvas, null);
        assertEquals(98, pixels[63][37]);
        renderer.render(null, canvas, null);
        assertEquals(128 * 128, writes.get());
        renderer.set(null, 0);
        renderer.render(null, canvas, null);
        assertEquals(34, pixels[63][37]);
        assertEquals(2 * 128 * 128, writes.get());
    }
}
