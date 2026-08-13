package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerCardMapRendererTest {
    @Test
    void amountCoordinatesMatchLocalRendererForItemsAndStatistics() {
        List<TextDraw> text = new ArrayList<>();
        MapCanvas canvas = recordingCanvas(text);

        WorkerCardMapRenderer.drawAmount(canvas, 2, 3, 4, false);
        WorkerCardMapRenderer.drawAmount(canvas, 2, 3, 12, true);

        assertEquals(List.of(
                new TextDraw(71, 93, "§47;4"),
                new TextDraw(70, 92, "§58;4"),
                new TextDraw(64, 92, "§47;12"),
                new TextDraw(63, 91, "§58;12")
        ), text);
    }

    @Test
    void statisticEntitySpriteUsesTheAdjustedTopRightOffset() {
        BufferedImage entity = TaskImageAtlas.entityImageFor(EntityType.ZOMBIE.key());
        BufferedImage cell = TaskImageAtlas.statisticCell(EntityType.ZOMBIE.key(), Statistic.KILL_ENTITY);

        int adjustedMatches = alignedOpaquePixels(cell, entity, 8, -1);
        int previousMatches = alignedOpaquePixels(cell, entity, 6, -4);
        assertTrue(adjustedMatches > previousMatches + 20,
                () -> "expected entity sprite at (+8,-1), matches=" + adjustedMatches
                        + "; old (+6,-4) matches=" + previousMatches);
    }

    private static int alignedOpaquePixels(BufferedImage cell, BufferedImage sprite, int offsetX, int offsetY) {
        int matches = 0;
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                int targetX = offsetX + x;
                int targetY = offsetY + y;
                if (targetX < 0 || targetX >= cell.getWidth() || targetY < 0 || targetY >= cell.getHeight()) continue;
                int source = sprite.getRGB(x, y);
                if ((source >>> 24) != 0xFF) continue;
                if (cell.getRGB(targetX, targetY) == source) matches++;
            }
        }
        return matches;
    }

    private record TextDraw(int x, int y, String text) {
    }

    private static MapCanvas recordingCanvas(List<TextDraw> text) {
        return (MapCanvas) Proxy.newProxyInstance(MapCanvas.class.getClassLoader(),
                new Class<?>[]{MapCanvas.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("drawText")) {
                        text.add(new TextDraw((int) arguments[0], (int) arguments[1], (String) arguments[3]));
                    }
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive() || returnType == void.class) return null;
                    if (returnType == boolean.class) return false;
                    if (returnType == byte.class) return (byte) 0;
                    if (returnType == short.class) return (short) 0;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == float.class) return 0F;
                    if (returnType == double.class) return 0D;
                    if (returnType == char.class) return '\0';
                    throw new IllegalStateException("Unsupported primitive return type: " + returnType);
                });
    }
}
