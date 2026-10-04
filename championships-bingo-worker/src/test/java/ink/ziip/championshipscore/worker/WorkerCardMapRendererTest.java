package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.platform.bukkit.bingo.map.MapColorMatcher;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import ink.ziip.championshipscore.protocol.BingoRuntimeRules;
import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchRunMode;
import ink.ziip.championshipscore.protocol.ProtocolVersion;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

import net.kyori.adventure.key.Key;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class WorkerCardMapRendererTest {
    @Test
    void eventAndDailyCardsShowOtherBordersUntilOwnCompletion() throws Exception {
        var draw =
                WorkerCardMapRenderer.class.getDeclaredMethod(
                        "drawBorders", MapCanvas.class, int.class, int.class, List.class);
        draw.setAccessible(true);
        for (MatchRunMode mode : new MatchRunMode[] {MatchRunMode.EVENT, MatchRunMode.DAILY}) {
            WorkerCardMapRenderer renderer = new WorkerCardMapRenderer(manifest(mode), 5, null);
            PixelCanvas pixels = new PixelCanvas();
            draw.invoke(renderer, pixels.canvas, 1, 1, List.of());
            assertTrue(pixels.colors.isEmpty());
            draw.invoke(renderer, pixels.canvas, 1, 1, List.of(1, 2));
            assertEquals(
                    Set.of(palette(255, 0, 0), palette(0, 0, 255)),
                    new HashSet<>(pixels.colors.values()));

            draw.invoke(renderer, pixels.canvas, 1, 1, List.of(1, 2, 3, 4, 5));
            assertEquals(160, pixels.colors.size());
            assertEquals(Set.of(palette(0, 255, 0)), new HashSet<>(pixels.colors.values()));

            PixelCanvas spectatorPixels = new PixelCanvas();
            draw.invoke(
                    new WorkerCardMapRenderer(manifest(mode), null),
                    spectatorPixels.canvas,
                    1,
                    1,
                    List.of(1, 2));
            assertEquals(
                    Set.of(palette(255, 0, 0), palette(0, 0, 255)),
                    new HashSet<>(spectatorPixels.colors.values()));
        }
    }

    @Test
    void playerAndSpectatorBordersShowAtMostSixTeams() throws Exception {
        var draw =
                WorkerCardMapRenderer.class.getDeclaredMethod(
                        "drawBorders", MapCanvas.class, int.class, int.class, List.class);
        draw.setAccessible(true);
        Set<Color> expected =
                Set.of(
                        palette(255, 0, 0),
                        palette(0, 0, 255),
                        palette(255, 255, 0),
                        palette(255, 255, 255),
                        palette(0, 255, 0),
                        palette(0, 255, 255));
        for (MatchRunMode mode : new MatchRunMode[] {MatchRunMode.EVENT, MatchRunMode.DAILY}) {
            for (Integer viewer : new Integer[] {8, null}) {
                WorkerCardMapRenderer renderer =
                        new WorkerCardMapRenderer(manifest(mode), viewer, null, null);
                PixelCanvas pixels = new PixelCanvas();
                draw.invoke(renderer, pixels.canvas, 1, 1, List.of(1, 2, 3, 4, 5, 6));
                assertEquals(expected, new HashSet<>(pixels.colors.values()));
                draw.invoke(renderer, pixels.canvas, 1, 1, List.of(1, 2, 3, 4, 5, 6, 7));
                assertEquals(expected, new HashSet<>(pixels.colors.values()));
                draw.invoke(renderer, pixels.canvas, 1, 1, List.of(1, 2, 3, 4, 5, 6, 7, 8));
                assertEquals(
                        viewer == null ? expected : Set.of(palette(170, 0, 170)),
                        new HashSet<>(pixels.colors.values()));
            }
        }
    }

    @Test
    void rowsColumnsAndBothDiagonalsRequireOwnCompletion() throws Exception {
        var draw =
                WorkerCardMapRenderer.class.getDeclaredMethod(
                        "drawCompletedLines",
                        MapCanvas.class,
                        Map.class,
                        int.class,
                        int.class,
                        int.class);
        draw.setAccessible(true);
        WorkerCardMapRenderer renderer =
                new WorkerCardMapRenderer(manifest(MatchRunMode.EVENT), 5, null);
        int[][] lines = {{0, 1, 2}, {0, 3, 6}, {0, 4, 8}, {2, 4, 6}};
        int[][] gaps = {{51, 40}, {40, 51}, {52, 52}, {76, 52}};
        for (int index = 0; index < lines.length; index++) {
            Map<Integer, List<Integer>> completions = new HashMap<>();
            for (int cell : lines[index]) completions.put(cell, List.of(1));
            completions.put(lines[index][0], List.of(1, 5));
            completions.put(lines[index][1], List.of(1, 5));
            PixelCanvas pixels = new PixelCanvas();
            draw.invoke(renderer, pixels.canvas, completions, 3, 1, 5);
            assertTrue(pixels.colors.isEmpty());
            completions.put(lines[index][2], List.of(1, 5));
            draw.invoke(renderer, pixels.canvas, completions, 3, 1, 5);
            assertEquals(
                    palette(0, 255, 0), pixels.colors.get(gaps[index][1] * 128 + gaps[index][0]));
        }
    }

    private static MatchManifest manifest(MatchRunMode mode) {
        List<BingoTaskSpec> tasks = new ArrayList<>();
        for (int cell = 0; cell < 9; cell++) {
            tasks.add(new BingoTaskSpec(cell, "task-" + cell, "item", Map.of("material", "STONE")));
        }
        return new MatchManifest(
                ProtocolVersion.CURRENT,
                UUID.randomUUID(),
                1,
                1,
                "worker",
                mode,
                600,
                1,
                "config",
                new BingoScoringRules(3, List.of(60, 50, 40, 30, 20), 50, 2, 20),
                new BingoRuntimeRules(5, 100, 10, 0, List.of()),
                tasks,
                List.of(
                        new TeamSnapshot(1, "Red", "red", "#ff0000", List.of()),
                        new TeamSnapshot(2, "Blue", "blue", "#0000ff", List.of()),
                        new TeamSnapshot(3, "Yellow", "yellow", "#ffff00", List.of()),
                        new TeamSnapshot(4, "White", "white", "#ffffff", List.of()),
                        new TeamSnapshot(5, "Green", "green", "#00ff00", List.of()),
                        new TeamSnapshot(6, "Aqua", "aqua", "#00ffff", List.of()),
                        new TeamSnapshot(7, "Black", "black", "#000000", List.of()),
                        new TeamSnapshot(8, "Purple", "purple", "#aa00aa", List.of())),
                List.of());
    }

    private static Color palette(int red, int green, int blue) {
        return MapColorMatcher.color(MapColorMatcher.matchColor(red, green, blue));
    }

    private static final class PixelCanvas {
        final Map<Integer, Color> colors = new HashMap<>();
        final MapCanvas canvas =
                (MapCanvas)
                        Proxy.newProxyInstance(
                                MapCanvas.class.getClassLoader(),
                                new Class<?>[] {MapCanvas.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("setPixelColor")) {
                                        colors.put(
                                                (int) args[1] * 128 + (int) args[0],
                                                (Color) args[2]);
                                        return null;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
    }

    @Test
    void amountCoordinatesMatchLocalRendererForItemsAndStatistics() {
        List<TextDraw> text = new ArrayList<>();
        MapCanvas canvas = recordingCanvas(text);

        WorkerCardMapRenderer.drawAmount(canvas, 2, 3, 4, false);
        WorkerCardMapRenderer.drawAmount(canvas, 2, 3, 12, true);

        assertEquals(
                List.of(
                        new TextDraw(71, 93, "§47;4"),
                        new TextDraw(70, 92, "§58;4"),
                        new TextDraw(64, 92, "§47;12"),
                        new TextDraw(63, 91, "§58;12")),
                text);
    }

    @Test
    void statisticEntitySpriteUsesTheAdjustedTopRightOffset() {
        BufferedImage entity = TaskImageAtlas.entityImageFor(EntityType.ZOMBIE.key());
        BufferedImage cell =
                TaskImageAtlas.statisticCell(EntityType.ZOMBIE.key(), Statistic.KILL_ENTITY);

        int adjustedMatches = alignedOpaquePixels(cell, entity, 8, -1);
        int previousMatches = alignedOpaquePixels(cell, entity, 6, -4);
        assertTrue(
                adjustedMatches > previousMatches + 20,
                () ->
                        "expected entity sprite at (+8,-1), matches="
                                + adjustedMatches
                                + "; old (+6,-4) matches="
                                + previousMatches);
    }

    @Test
    void travelStatisticsUseTheSharedArrowBadge() {
        BufferedImage arrow = TaskImageAtlas.eventBadgeImage(Key.key("minecraft", "travel_arrow"));
        assertNotNull(arrow);
        BufferedImage cell =
                TaskImageAtlas.statisticCell(Material.LEATHER_BOOTS.key(), Statistic.WALK_ONE_CM);

        int matches = alignedOpaquePixels(cell, arrow, -4, 5);
        assertTrue(matches >= 25, () -> "expected travel arrow at (-4,+5), matches=" + matches);
        assertTrue(
                opaqueColors(arrow).size() >= 3,
                "travel arrow should keep outline, face, and shadow tones");
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
        assertTrue(
                opaqueWidth <= 13,
                () -> "check should leave room for the task subject, width=" + opaqueWidth);
        assertTrue(
                opaqueHeight <= 11,
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

    private static int alignedOpaquePixels(
            BufferedImage cell, BufferedImage sprite, int offsetX, int offsetY) {
        int matches = 0;
        for (int y = 0; y < sprite.getHeight(); y++) {
            for (int x = 0; x < sprite.getWidth(); x++) {
                int targetX = offsetX + x;
                int targetY = offsetY + y;
                if (targetX < 0
                        || targetX >= cell.getWidth()
                        || targetY < 0
                        || targetY >= cell.getHeight()) continue;
                int source = sprite.getRGB(x, y);
                if ((source >>> 24) != 0xFF) continue;
                if (cell.getRGB(targetX, targetY) == source) matches++;
            }
        }
        return matches;
    }

    private record TextDraw(int x, int y, String text) {}

    private static MapCanvas recordingCanvas(List<TextDraw> text) {
        return (MapCanvas)
                Proxy.newProxyInstance(
                        MapCanvas.class.getClassLoader(),
                        new Class<?>[] {MapCanvas.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("drawText")) {
                                text.add(
                                        new TextDraw(
                                                (int) arguments[0],
                                                (int) arguments[1],
                                                (String) arguments[3]));
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
                            throw new IllegalStateException(
                                    "Unsupported primitive return type: " + returnType);
                        });
    }
}
