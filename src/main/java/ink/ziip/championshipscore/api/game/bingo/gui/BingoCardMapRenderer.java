package ink.ziip.championshipscore.api.game.bingo.gui;

import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.bingo.game.RoundOutcome;
import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.CardDisplayInfo;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.PotionTask;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a team's live bingo card onto a 128x128 filled-map using the real item textures from
 * {@link TaskImageAtlas}. Only redraws when the card state changes.
 */
public final class BingoCardMapRenderer extends MapRenderer {
    private final BingoCard card;
    private final String teamId;
    private final TextColor teamColor;
    /** Fixed number of border segments (0 = dynamic, one per completing team; 2 or 4 = points-mode tiers). */
    private final int tierSegments;
    /** Set when the round ends so this renderer paints the win-state overlay. Null while running. */
    private final @Nullable BingoRound round;
    private String lastState;

    public BingoCardMapRenderer(BingoCard card, String teamId, TextColor teamColor) {
        this(card, teamId, teamColor, 0, null);
    }

    public BingoCardMapRenderer(BingoCard card, String teamId, TextColor teamColor, int tierSegments) {
        this(card, teamId, teamColor, tierSegments, null);
    }

    public BingoCardMapRenderer(BingoCard card, String teamId, TextColor teamColor, int tierSegments, @Nullable BingoRound round) {
        super(false);
        this.card = card;
        this.teamId = teamId;
        this.teamColor = teamColor;
        this.tierSegments = Math.clamp(tierSegments, 0, 4);
        this.round = round;
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        String state = stateSignature();
        if (state.equals(lastState)) return;

        BufferedImage background = TaskImageAtlas.background();
        if (background != null) drawImage(canvas, 0, 0, background, null);

        int n = card.size.size;
        int offset = (5 - n) / 2; // centre smaller cards on the 5x5 map layout
        var tasks = card.getTasks();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                drawTask(canvas, tasks.get(y * n + x), x + offset, y + offset);
            }
        }

        RoundOutcome outcome = round != null ? round.outcome() : null;
        if (outcome != null && outcome.winnerId() != null) {
            drawWinOverlay(canvas, outcome, offset, n);
        }

        lastState = state;
    }

    private void drawTask(MapCanvas canvas, GameTask task, int gridX, int gridY) {
        if (task.isHidden()) {
            drawHidden(canvas, gridX, gridY);
            return;
        }
        Key key = task.data.getDisplayMaterial(CardDisplayInfo.DEFAULT).key();
        int x = gridX * 24 + 4;
        int y = gridY * 24 + 4;

        boolean isStatistic = task.data instanceof StatisticTask;
        if (task.data instanceof StatisticTask statisticTask) {
            Key cellKey = key;
            if (statisticTask.statistic().hasEntity()) {
                cellKey = statisticTask.statistic().entityType().key();
            }
            BufferedImage cell = TaskImageAtlas.statisticCell(cellKey, statisticTask.statistic().statisticType());
            drawImage(canvas, x, y, cell, null);
        } else if (task.data instanceof OneOfTask) {
            // one_of: the representative item plus a small top-right "stack" badge marking the cell as
            // "any one of a set" — far clearer than the old four-member quadrant collage.
            BufferedImage image = TaskImageAtlas.imageFor(key);
            if (image != null) {
                drawImage(canvas, x + 1, y + 1, image, null);
            }
            drawSetBadge(canvas, x, y);
        } else if (task.data instanceof PotionTask potion) {
            // Effect-specific potion: its own per-effect coloured sprite (falls back to the plain
            // potion-material icon if that effect isn't bundled).
            BufferedImage image = TaskImageAtlas.potionImageFor(potion.form().infix, potion.effect());
            if (image == null) image = TaskImageAtlas.imageFor(key);
            if (image != null) {
                drawImage(canvas, x + 1, y + 1, image, null);
            }
        } else {
            if (task.data instanceof AdvancementTask advancement) {
                BufferedImage frame = TaskImageAtlas.advancementFrame(advancement.frameType());
                if (frame != null) drawImageClipped(canvas, x - 1, y - 1, frame, x, y, 24, 24);
            }

            BufferedImage image = TaskImageAtlas.imageFor(key);
            if (image != null) {
                drawImage(canvas, x + 1, y + 1, image, null);
            }
        }

        int amount = task.data.getRequiredAmount();
        if (amount > 1 || isStatistic) drawAmount(canvas, gridX, gridY, amount, isStatistic);

        drawCompletionBorder(canvas, gridX, gridY, completionColors(task), tierSegments);
    }

    private List<TextColor> completionColors(GameTask task) {
        List<TextColor> colors = new ArrayList<>(4);
        int limit = tierSegments > 0 ? tierSegments : 4;
        for (GameTask.Completion c : task.allCompletions()) {
            if (colors.size() >= limit) break;
            if (c.teamColor() != null) colors.add(c.teamColor());
        }
        return colors;
    }

    private static void drawCompletionBorder(MapCanvas canvas, int gridX, int gridY,
                                             List<TextColor> teams, int forceSegments) {
        int filled = Math.min(teams.size(), 4);
        int segments = forceSegments > 0 ? forceSegments : Math.max(filled, 1);
        if (filled == 0) return;
        final int ox = gridX * 24 + 4, oy = gridY * 24 + 4, size = 24;
        final int inset = 1, thickness = 2;
        final int lo = inset, hi = size - inset;
        byte[] idx = new byte[filled];
        for (int i = 0; i < filled; i++) {
            TextColor c = teams.get(i);
            idx[i] = MapColorMatcher.matchColor(c.red(), c.green(), c.blue());
        }
        double cx = ox + size / 2.0 - 0.5, cy = oy + size / 2.0 - 0.5;
        for (int dy = lo; dy < hi; dy++) {
            for (int dx = lo; dx < hi; dx++) {
                boolean onRing = dx < lo + thickness || dx >= hi - thickness
                        || dy < lo + thickness || dy >= hi - thickness;
                if (!onRing) continue;
                if (segments == 1) {
                    canvas.setPixel(ox + dx, oy + dy, idx[0]);
                    continue;
                }
                double ang = Math.atan2((oy + dy) - cy, (ox + dx) - cx);
                double norm = (ang + Math.PI / 2) / (2 * Math.PI);
                norm -= Math.floor(norm);
                int seg = (int) (norm * segments) % segments;
                if (seg < filled) canvas.setPixel(ox + dx, oy + dy, idx[seg]);
            }
        }
    }

    private static void drawHidden(MapCanvas canvas, int gridX, int gridY) {
        final int ox = gridX * 24 + 4, oy = gridY * 24 + 4, size = 24, inset = 1;
        byte dark = MapColorMatcher.matchColor(74, 74, 78);
        for (int dy = inset; dy < size - inset; dy++) {
            for (int dx = inset; dx < size - inset; dx++) {
                canvas.setPixel(ox + dx, oy + dy, dark);
            }
        }
    }

    private void drawAmount(MapCanvas canvas, int gridX, int gridY, int amount, boolean shiftUpLeft) {
        String text = Integer.toString(amount);
        int xStart = text.length() == 1 ? 6 : 0;
        int d = shiftUpLeft ? -1 : 0;
        canvas.drawText(gridX * 24 + 17 + xStart + d, gridY * 24 + 21 + d, MinecraftFont.Font, "§47;" + amount);
        canvas.drawText(gridX * 24 + 16 + xStart + d, gridY * 24 + 20 + d, MinecraftFont.Font, "§58;" + amount);
    }

    /** Glyph width/height of the tiny built-in "ANY" font, and the 1px gap between letters. */
    private static final int GLYPH_W = 4;
    private static final int GLYPH_H = 5;
    private static final int GLYPH_GAP = 1;
    /**
     * A short 4×5 pixel font spelling the word the cell badge needs ("ANY"). Each letter is five rows;
     * each row's low {@value #GLYPH_W} bits are its columns, most-significant bit leftmost.
     */
    private static final int[][] ANY_GLYPHS = {
            {0b0110, 0b1001, 0b1111, 0b1001, 0b1001}, // A
            {0b1001, 0b1101, 0b1011, 0b1001, 0b1001}, // N
            {0b1001, 0b1001, 0b0110, 0b0010, 0b0010}, // Y
    };

    /**
     * Stamps a small yellow "ANY" label across the top of a {@code one_of} cell, marking it as "collect
     * any one of a set". Kept along the top edge (clear of the bottom-right amount and bottom-left
     * statistic badge) and drawn with a 1px dark drop-shadow so the word stays legible on any item
     * beneath. {@code (x,y)} is the cell's slot origin (top-left of the 24px slot).
     */
    private static void drawSetBadge(MapCanvas canvas, int x, int y) {
        byte fg = MapColorMatcher.matchColor(255, 221, 85);  // yellow, matching the task name colour
        byte shadow = MapColorMatcher.matchColor(28, 28, 30);
        int total = ANY_GLYPHS.length * GLYPH_W + (ANY_GLYPHS.length - 1) * GLYPH_GAP; // 14px
        int startX = x + (24 - total) / 2; // centred on the slot
        int startY = y + 2;
        // Two passes (all shadow, then all foreground) so adjacent letters' fill never eats a shadow.
        for (int pass = 0; pass < 2; pass++) {
            byte colour = pass == 0 ? shadow : fg;
            int dx = pass == 0 ? 1 : 0, dy = pass == 0 ? 1 : 0;
            for (int gi = 0; gi < ANY_GLYPHS.length; gi++) {
                int gx = startX + gi * (GLYPH_W + GLYPH_GAP);
                int[] glyph = ANY_GLYPHS[gi];
                for (int row = 0; row < GLYPH_H; row++) {
                    for (int col = 0; col < GLYPH_W; col++) {
                        if ((glyph[row] & (1 << (GLYPH_W - 1 - col))) == 0) continue;
                        int px = gx + col + dx, py = startY + row + dy;
                        if (px >= 0 && px < 128 && py >= 0 && py < 128) canvas.setPixel(px, py, colour);
                    }
                }
            }
        }
    }

    private static void drawImage(MapCanvas canvas, int x, int y, BufferedImage image, @Nullable TextColor modulate) {
        int w = image.getWidth(), h = image.getHeight();
        byte[] indices = MapColorMatcher.indices(image, modulate);
        for (int iy = 0; iy < h; iy++) {
            for (int ix = 0; ix < w; ix++) {
                byte index = indices[iy * w + ix];
                if (index == MapColorMatcher.TRANSPARENT) continue;
                canvas.setPixel(x + ix, y + iy, index);
            }
        }
    }

    private static void drawImageClipped(MapCanvas canvas, int x, int y, BufferedImage image,
                                         int clipX, int clipY, int clipW, int clipH) {
        int w = image.getWidth(), h = image.getHeight();
        byte[] indices = MapColorMatcher.indices(image, null);
        for (int iy = 0; iy < h; iy++) {
            int py = y + iy;
            if (py < clipY || py >= clipY + clipH) continue;
            for (int ix = 0; ix < w; ix++) {
                int px = x + ix;
                if (px < clipX || px >= clipX + clipW) continue;
                byte index = indices[iy * w + ix];
                if (index == MapColorMatcher.TRANSPARENT) continue;
                canvas.setPixel(px, py, index);
            }
        }
    }

    // ── win-state overlay ────────────────────────────────────────────────────────────────────────

    private void drawWinOverlay(MapCanvas canvas, RoundOutcome outcome, int offset, int n) {
        if (outcome.winnerId() == null) return;
        // Only paint on the WINNING team's card.
        if (!outcome.winnerId().equals(teamId)) return;

        TextColor color = outcome.winnerColor() != null ? outcome.winnerColor() : teamColor;
        byte palette = MapColorMatcher.matchColor(color.red(), color.green(), color.blue());

        switch (outcome.type()) {
            case LINES -> drawWinningLines(canvas, palette, offset, n);
            case FULL_CARD, MOST_COMPLETED, TOP_SCORE -> drawWinningCellHighlights(canvas, palette, offset, n);
            case DRAW -> { /* no overlay on a draw */ }
        }
    }

    private void drawWinningLines(MapCanvas canvas, byte palette, int offset, int n) {
        List<int[]> lines = card.completedLines(teamId);
        for (int[] line : lines) {
            int firstIdx = line[0];
            int lastIdx = line[line.length - 1];
            int sx = (firstIdx % n + offset) * 24 + 4 + 12;
            int sy = (firstIdx / n + offset) * 24 + 4 + 12;
            int ex = (lastIdx % n + offset) * 24 + 4 + 12;
            int ey = (lastIdx / n + offset) * 24 + 4 + 12;
            int dxv = ex - sx, dyv = ey - sy;
            double len = Math.hypot(dxv, dyv);
            if (len < 1) continue;
            double extend = 8.0;
            int sx2 = (int) Math.round(sx - dxv / len * extend);
            int sy2 = (int) Math.round(sy - dyv / len * extend);
            int ex2 = (int) Math.round(ex + dxv / len * extend);
            int ey2 = (int) Math.round(ey + dyv / len * extend);
            long seed = ((long) firstIdx * 73856093L) ^ ((long) lastIdx * 19349663L);
            drawScribbleLine(canvas, sx2, sy2, ex2, ey2, palette, 3, seed);
        }
    }

    private static void drawScribbleLine(MapCanvas canvas, int x1, int y1, int x2, int y2,
                                         byte palette, int radius, long seed) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        double nx = -dy / len, ny = dx / len;
        int steps = (int) Math.ceil(len * 2);
        long s = seed == 0 ? 1 : seed;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            s ^= s << 13;
            s ^= s >>> 7;
            s ^= s << 17;
            double wobble = ((s & 0x3) - 1.5) * 0.6;
            int cx = (int) Math.round(x1 + dx * t + nx * wobble);
            int cy = (int) Math.round(y1 + dy * t + ny * wobble);
            stampBrush(canvas, cx, cy, radius, palette);
        }
    }

    private static void stampBrush(MapCanvas canvas, int cx, int cy, int r, byte palette) {
        for (int dy = -r; dy <= r; dy++) {
            int py = cy + dy;
            if (py < 0 || py >= 128) continue;
            for (int dx = -r; dx <= r; dx++) {
                int px = cx + dx;
                if (px < 0 || px >= 128) continue;
                if (dx * dx + dy * dy > r * r + r) continue;
                canvas.setPixel(px, py, palette);
            }
        }
    }

    private void drawWinningCellHighlights(MapCanvas canvas, byte palette, int offset, int n) {
        int[] indices = card.completedIndices(teamId);
        for (int idx : indices) {
            int gridX = idx % n + offset;
            int gridY = idx / n + offset;
            drawHaloAndHatch(canvas, gridX, gridY, palette);
        }
    }

    private static void drawHaloAndHatch(MapCanvas canvas, int gridX, int gridY, byte palette) {
        final int ox = gridX * 24 + 4, oy = gridY * 24 + 4, size = 24;
        final int halo = 2;
        for (int dy = 1; dy < size - 1; dy++) {
            for (int dx = 1; dx < size - 1; dx++) {
                boolean onRing = dx < 1 + halo || dx >= size - 1 - halo
                        || dy < 1 + halo || dy >= size - 1 - halo;
                if (!onRing) continue;
                canvas.setPixel(ox + dx, oy + dy, palette);
            }
        }
        for (int dy = 1 + halo; dy < size - 1 - halo; dy++) {
            for (int dx = 1 + halo; dx < size - 1 - halo; dx++) {
                if (((dx - dy) % 5 + 5) % 5 != 0) continue;
                canvas.setPixel(ox + dx, oy + dy, palette);
            }
        }
    }

    private String stateSignature() {
        StringBuilder sb = new StringBuilder(card.getTasks().size() * 2 + 16);
        for (GameTask task : card.getTasks()) {
            sb.append(task.isHidden() ? 'h' : task.isVoided() ? 'v' : task.isCompletedByTeam(teamId) ? 'x' : '.');
            int n = task.allCompletions().size();
            sb.append((char) ('0' + Math.min(n, 9)));
        }
        RoundOutcome o = round != null ? round.outcome() : null;
        if (o != null) {
            sb.append('|').append(o.type().name());
            if (o.winnerId() != null) sb.append(':').append(o.winnerId());
        }
        return sb.toString();
    }
}
