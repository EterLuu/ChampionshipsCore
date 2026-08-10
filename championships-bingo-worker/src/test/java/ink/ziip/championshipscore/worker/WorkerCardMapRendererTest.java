package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerCardMapRendererTest {
    @Test
    void amountCoordinatesMatchLocalRendererForItemsAndStatistics() {
        RecordingCanvas canvas = new RecordingCanvas();

        WorkerCardMapRenderer.drawAmount(canvas, 2, 3, 4, false);
        WorkerCardMapRenderer.drawAmount(canvas, 2, 3, 12, true);

        assertEquals(List.of(
                new TextDraw(71, 93, "§47;4"),
                new TextDraw(70, 92, "§58;4"),
                new TextDraw(64, 92, "§47;12"),
                new TextDraw(63, 91, "§58;12")
        ), canvas.text);
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

    private static final class RecordingCanvas implements MapCanvas {
        private final List<TextDraw> text = new ArrayList<>();

        @Override public MapView getMapView() { return null; }
        @Override public MapCursorCollection getCursors() { return new MapCursorCollection(); }
        @Override public void setCursors(MapCursorCollection cursors) { }
        @Override public void setPixelColor(int x, int y, Color color) { }
        @Override public Color getPixelColor(int x, int y) { return null; }
        @Override public Color getBasePixelColor(int x, int y) { return null; }
        @Override public void setPixel(int x, int y, byte color) { }
        @Override public byte getPixel(int x, int y) { return 0; }
        @Override public byte getBasePixel(int x, int y) { return 0; }
        @Override public void drawImage(int x, int y, Image image) { }
        @Override public void drawText(int x, int y, MapFont font, String text) {
            this.text.add(new TextDraw(x, y, text));
        }
    }
}
