package ink.ziip.championshipscore.platform.bukkit.bingo.map;

import org.bukkit.map.MapCanvas;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Shared composition and amount layout for framed 24x24 statistic/event card cells. */
public final class TaskCellLayout {
    public static final int CELL = 24;
    public static final int FRAME_BORDER = 2;
    private static final int BADGE_SIZE = 16;

    private TaskCellLayout() {
    }

    public static BufferedImage compose(Color panelColor, Color borderColor,
                                        @Nullable BufferedImage subject, boolean entitySubject,
                                        @Nullable BufferedImage badge) {
        BufferedImage tile = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tile.createGraphics();
        int panel = CELL - 2;
        graphics.setColor(panelColor);
        graphics.fillRect(1, 1, panel, panel);
        graphics.setColor(borderColor);
        graphics.drawRect(1, 1, panel - 1, panel - 1);
        graphics.setClip(FRAME_BORDER, FRAME_BORDER, CELL - 2 * FRAME_BORDER, CELL - 2 * FRAME_BORDER);
        if (subject != null) {
            int nudgeX = badge == null ? 0 : 5;
            int nudgeY = badge == null ? 0 : -5;
            // Keep ChampionshipsCore's established entity alignment. Its atlas sprites need a
            // two-pixel right / three-pixel down correction relative to the padded item sprites.
            graphics.drawImage(subject, 1 + nudgeX + (entitySubject ? 2 : 0),
                    1 + nudgeY + (entitySubject ? 3 : 0), null);
        }
        if (badge != null) {
            int badgeX = FRAME_BORDER - 2;
            int badgeY = CELL - BADGE_SIZE - FRAME_BORDER + 1;
            graphics.drawImage(badge, badgeX - 4, badgeY - 2, null);
        }
        graphics.dispose();
        return tile;
    }

    public static void drawAmount(MapCanvas canvas, int gridX, int gridY, int amount, boolean shiftUpLeft) {
        String text = Integer.toString(amount);
        int xStart = text.length() == 1 ? 6 : 0;
        int delta = shiftUpLeft ? -1 : 0;
        canvas.drawText(gridX * 24 + 17 + xStart + delta, gridY * 24 + 21 + delta,
                MinecraftFont.Font, "§47;" + amount);
        canvas.drawText(gridX * 24 + 16 + xStart + delta, gridY * 24 + 20 + delta,
                MinecraftFont.Font, "§58;" + amount);
    }
}
