package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void travelStatisticsUseTheSharedArrowBadge() {
        BufferedImage arrow = TaskImageAtlas.eventBadgeImage(Key.key("minecraft", "travel_arrow"));
        assertNotNull(arrow);
        BufferedImage cell = TaskImageAtlas.statisticCell(Material.LEATHER_BOOTS.key(), Statistic.WALK_ONE_CM);

        int matches = alignedOpaquePixels(cell, arrow, -4, 5);
        assertTrue(matches >= 25, () -> "expected travel arrow at (-4,+5), matches=" + matches);
        assertTrue(opaqueColors(arrow).size() >= 3, "travel arrow should keep outline, face, and shadow tones");
    }

    @Test
    void checkBadgeIsCompactMultitonePixelArt() {
        BufferedImage check = TaskImageAtlas.checkBadge();
        assertNotNull(check);
        Set<Integer> colors = new HashSet<>();
        int minX = check.getWidth();
        int minY = check.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < check.getHeight(); y++) {
            for (int x = 0; x < check.getWidth(); x++) {
                int pixel = check.getRGB(x, y);
                if ((pixel >>> 24) == 0) continue;
                colors.add(pixel);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        assertTrue(colors.size() >= 3, () -> "expected multitone check, colors=" + colors.size());
        int opaqueWidth = maxX - minX + 1;
        int opaqueHeight = maxY - minY + 1;
        assertTrue(opaqueWidth <= 13,
                () -> "check should leave room for the task subject, width=" + opaqueWidth);
        assertTrue(opaqueHeight <= 11,
                () -> "check should remain a compact corner badge, height=" + opaqueHeight);
    }

    private static Set<Integer> opaqueColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) == 0xFF) colors.add(pixel);
            }
        }
        return colors;
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
