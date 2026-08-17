package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.map.MapColorMatcher;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Live map-card renderer backed only by the frozen wire task model and worker replay state. */
final class WorkerCardMapRenderer extends MapRenderer {
    private final MatchManifest manifest;
    private final int viewerTeam;
    private final WorkerMatchSession session;
    private String lastSignature;

    WorkerCardMapRenderer(MatchManifest manifest, int viewerTeam, WorkerMatchSession session) {
        super(false);
        this.manifest = manifest;
        this.viewerTeam = viewerTeam;
        this.session = session;
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        Map<Integer, List<Integer>> completions = session.completionSnapshot();
        Integer winner = session.winnerTeamId();
        String signature = completions + "|winner=" + winner;
        if (signature.equals(lastSignature)) return;
        BufferedImage background = TaskImageAtlas.background();
        if (background != null) drawImage(canvas, 0, 0, background);

        int width = manifest.scoring().cardWidth();
        int offset = (5 - width) / 2;
        for (BingoTaskSpec task : manifest.tasks()) {
            int gridX = task.cellIndex() % width + offset;
            int gridY = task.cellIndex() / width + offset;
            drawTask(canvas, task, gridX, gridY);
            drawBorders(canvas, gridX, gridY, completions.getOrDefault(task.cellIndex(), List.of()));
        }
        if (winner == null) {
            drawCompletedLines(canvas, completions, width, offset, viewerTeam);
        } else {
            // Local Bingo ends in TOP_SCORE mode: the final overlay highlights every cell completed
            // by the winner rather than pretending that one of their completed lines decided it.
            drawWinningCellHighlights(canvas, completions, width, offset, winner);
        }
        lastSignature = signature;
    }

    private void drawTask(MapCanvas canvas, BingoTaskSpec task, int gridX, int gridY) {
        int x = gridX * 24 + 4;
        int y = gridY * 24 + 4;
        if ("statistic".equals(task.taskType())) {
            Key subject = WorkerTaskDisplay.statisticSubject(task);
            Statistic statistic = WorkerTaskDisplay.enumValue(Statistic.class, task.attributes().get("statistic"));
            boolean any = Boolean.parseBoolean(task.attributes().get("display.any-template"));
            BufferedImage cell = statistic == null ? null : any
                    ? TaskImageAtlas.statisticCell(subject, (BufferedImage) null)
                    : TaskImageAtlas.statisticCell(subject, statistic);
            // statisticCell is already the full 24x24 slot, unlike the normal 22x22 sprites.
            if (cell != null) drawImage(canvas, x, y, cell);
            if (any) drawSetBadge(canvas, x, y);
        } else if ("event".equals(task.taskType())) {
            Key subject = WorkerTaskDisplay.key(task.attributes().get("display.icon-key"));
            if (subject == null) subject = WorkerTaskDisplay.key(task.attributes().get("display.entity"));
            if (subject == null) subject = WorkerTaskDisplay.icon(task).key();
            boolean any = Boolean.parseBoolean(task.attributes().get("display.any-template"));
            BufferedImage badge = null;
            if (Boolean.parseBoolean(task.attributes().get("display.green-check"))) {
                badge = TaskImageAtlas.checkBadge();
            } else {
                Key badgeKey = WorkerTaskDisplay.key(task.attributes().get("display.badge-key"));
                if (badgeKey != null) badge = TaskImageAtlas.imageFor(badgeKey);
            }
            BufferedImage cell = TaskImageAtlas.eventCell(subject, any ? null : badge);
            drawImage(canvas, x, y, cell);
            if (any) drawSetBadge(canvas, x, y);
        } else if ("potion".equals(task.taskType())) {
            Material material = WorkerTaskDisplay.icon(task);
            BufferedImage image = TaskImageAtlas.potionImageFor(potionInfix(material), task.attributes().get("effect"));
            if (image == null) image = atlas(material);
            if (image != null) drawImage(canvas, x + 1, y + 1, image);
        } else if ("item_set".equals(task.taskType())) {
            BufferedImage image = atlas(WorkerTaskDisplay.icon(task));
            if (image != null) drawImage(canvas, x + 1, y + 1, image);
            drawSetBadge(canvas, x, y);
        } else if ("all_of".equals(task.taskType())) {
            BufferedImage image = atlas(WorkerTaskDisplay.icon(task));
            if (image != null) drawImage(canvas, x + 1, y + 1, image);
            drawAllBadge(canvas, x, y);
        } else {
            Material icon = WorkerTaskDisplay.icon(task);
            if ("advancement".equals(task.taskType())) {
                Advancement advancement = WorkerTaskDisplay.advancement(task.attributes().get("key"));
                if (advancement != null && advancement.getDisplay() != null) {
                    BufferedImage frame = TaskImageAtlas.advancementFrame(advancement.getDisplay().frame());
                    if (frame != null) drawImageClipped(canvas, x - 1, y - 1, frame, x, y, 24, 24);
                }
            }
            Key frozenKey = WorkerTaskDisplay.key(task.attributes().get("display.icon-key"));
            BufferedImage image = frozenKey == null ? atlas(icon) : TaskImageAtlas.imageFor(frozenKey);
            if (image != null) drawImage(canvas, x + 1, y + 1, image);
        }
        int amount = WorkerTaskDisplay.amount(task);
        if (amount > 1 || "statistic".equals(task.taskType())) {
            drawAmount(canvas, gridX, gridY, amount,
                    "statistic".equals(task.taskType()) || "event".equals(task.taskType()));
        }
    }

    private void drawBorders(MapCanvas canvas, int gridX, int gridY, List<Integer> completedTeams) {
        int filled = Math.min(completedTeams.size(), 4);
        if (filled == 0) return;
        int segments = Math.max(filled, 1);
        final int ox = gridX * 24 + 4, oy = gridY * 24 + 4, size = 24;
        final int inset = 1, thickness = 2;
        final int lo = inset, hi = size - inset;
        byte[] colors = new byte[filled];
        for (int index = 0; index < filled; index++) colors[index] = teamColor(completedTeams.get(index));
        double cx = ox + size / 2.0 - 0.5, cy = oy + size / 2.0 - 0.5;
        for (int dy = lo; dy < hi; dy++) {
            for (int dx = lo; dx < hi; dx++) {
                boolean onRing = dx < lo + thickness || dx >= hi - thickness
                        || dy < lo + thickness || dy >= hi - thickness;
                if (!onRing) continue;
                if (segments == 1) {
                    pixel(canvas, ox + dx, oy + dy, colors[0]);
                    continue;
                }
                double angle = Math.atan2((oy + dy) - cy, (ox + dx) - cx);
                double normalized = (angle + Math.PI / 2) / (2 * Math.PI);
                normalized -= Math.floor(normalized);
                int segment = (int) (normalized * segments) % segments;
                if (segment < filled) pixel(canvas, ox + dx, oy + dy, colors[segment]);
            }
        }
    }

    private void drawCompletedLines(MapCanvas canvas, Map<Integer, List<Integer>> completions,
                                    int width, int offset, int lineTeam) {
        List<int[]> lines = new ArrayList<>();
        for (int row = 0; row < width; row++) {
            int[] line = new int[width];
            for (int col = 0; col < width; col++) line[col] = row * width + col;
            lines.add(line);
        }
        for (int col = 0; col < width; col++) {
            int[] line = new int[width];
            for (int row = 0; row < width; row++) line[row] = row * width + col;
            lines.add(line);
        }
        int[] diagonal = new int[width];
        int[] reverse = new int[width];
        for (int index = 0; index < width; index++) {
            diagonal[index] = index * width + index;
            reverse[index] = index * width + (width - 1 - index);
        }
        lines.add(diagonal);
        lines.add(reverse);

        byte color = teamColor(lineTeam);
        for (int[] line : lines) {
            boolean complete = true;
            for (int cell : line) {
                if (!completions.getOrDefault(cell, List.of()).contains(lineTeam)) {
                    complete = false;
                    break;
                }
            }
            if (!complete) continue;
            int first = line[0], last = line[line.length - 1];
            int startX = (first % width + offset) * 24 + 16;
            int startY = (first / width + offset) * 24 + 16;
            int endX = (last % width + offset) * 24 + 16;
            int endY = (last / width + offset) * 24 + 16;
            int dx = endX - startX, dy = endY - startY;
            double length = Math.hypot(dx, dy);
            if (length < 1) continue;
            double extend = 8.0;
            int extendedStartX = (int) Math.round(startX - dx / length * extend);
            int extendedStartY = (int) Math.round(startY - dy / length * extend);
            int extendedEndX = (int) Math.round(endX + dx / length * extend);
            int extendedEndY = (int) Math.round(endY + dy / length * extend);
            long seed = ((long) first * 73856093L) ^ ((long) last * 19349663L);
            drawScribbleLine(canvas, extendedStartX, extendedStartY, extendedEndX, extendedEndY,
                    color, 3, seed);
        }
    }

    private void drawWinningCellHighlights(MapCanvas canvas, Map<Integer, List<Integer>> completions,
                                           int width, int offset, int winner) {
        byte color = teamColor(winner);
        for (int cell = 0; cell < manifest.tasks().size(); cell++) {
            if (!completions.getOrDefault(cell, List.of()).contains(winner)) continue;
            drawHaloAndHatch(canvas, cell % width + offset, cell / width + offset, color);
        }
    }

    private byte teamColor(int teamId) {
        TeamSnapshot team = manifest.teamsById().get(teamId);
        if (team == null) return MapColorMatcher.matchColor(255, 255, 255);
        try {
            String raw = team.colorCode().replace("#", "").replace("&", "");
            int rgb = Integer.parseInt(raw, 16);
            return MapColorMatcher.matchColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
        } catch (RuntimeException ignored) {
            return MapColorMatcher.matchColor(255, 255, 255);
        }
    }

    private static BufferedImage atlas(Material material) {
        return material == null ? null : TaskImageAtlas.imageFor(material.key());
    }

    private static String potionInfix(Material material) {
        return switch (material) {
            case SPLASH_POTION -> "splash_potion";
            case LINGERING_POTION -> "lingering_potion";
            default -> "potion";
        };
    }

    static void drawAmount(MapCanvas canvas, int gridX, int gridY, int amount, boolean shiftUpLeft) {
        String text = Integer.toString(amount);
        int xStart = text.length() == 1 ? 6 : 0;
        int delta = shiftUpLeft ? -1 : 0;
        canvas.drawText(gridX * 24 + 17 + xStart + delta, gridY * 24 + 21 + delta,
                MinecraftFont.Font, "§47;" + amount);
        canvas.drawText(gridX * 24 + 16 + xStart + delta, gridY * 24 + 20 + delta,
                MinecraftFont.Font, "§58;" + amount);
    }

    private static final int GLYPH_W = 4;
    private static final int GLYPH_H = 5;
    private static final int GLYPH_GAP = 1;
    private static final int[][] ANY_GLYPHS = {
            {0b0110, 0b1001, 0b1111, 0b1001, 0b1001},
            {0b1001, 0b1101, 0b1011, 0b1001, 0b1001},
            {0b1001, 0b1001, 0b0110, 0b0010, 0b0010},
    };
    private static final int[][] ALL_GLYPHS = {
            {0b0110, 0b1001, 0b1111, 0b1001, 0b1001},
            {0b1000, 0b1000, 0b1000, 0b1000, 0b1111},
            {0b1000, 0b1000, 0b1000, 0b1000, 0b1111},
    };

    private static void drawSetBadge(MapCanvas canvas, int x, int y) {
        drawWordBadge(canvas, x, y, ANY_GLYPHS);
    }

    private static void drawAllBadge(MapCanvas canvas, int x, int y) {
        drawWordBadge(canvas, x, y, ALL_GLYPHS);
    }

    private static void drawWordBadge(MapCanvas canvas, int x, int y, int[][] glyphs) {
        byte foreground = MapColorMatcher.matchColor(255, 221, 85);
        byte shadow = MapColorMatcher.matchColor(28, 28, 30);
        int total = glyphs.length * GLYPH_W + (glyphs.length - 1) * GLYPH_GAP;
        int startX = x + (24 - total) / 2;
        int startY = y + 2;
        for (int pass = 0; pass < 2; pass++) {
            byte color = pass == 0 ? shadow : foreground;
            int nudgeX = pass == 0 ? 1 : 0;
            int nudgeY = pass == 0 ? 1 : 0;
            for (int glyphIndex = 0; glyphIndex < glyphs.length; glyphIndex++) {
                int glyphX = startX + glyphIndex * (GLYPH_W + GLYPH_GAP);
                int[] glyph = glyphs[glyphIndex];
                for (int row = 0; row < GLYPH_H; row++) {
                    for (int column = 0; column < GLYPH_W; column++) {
                        if ((glyph[row] & (1 << (GLYPH_W - 1 - column))) == 0) continue;
                        pixel(canvas, glyphX + column + nudgeX, startY + row + nudgeY, color);
                    }
                }
            }
        }
    }

    private static void drawImage(MapCanvas canvas, int x, int y, BufferedImage image) {
        byte[] colors = MapColorMatcher.indices(image);
        for (int iy = 0; iy < image.getHeight(); iy++) {
            for (int ix = 0; ix < image.getWidth(); ix++) {
                byte color = colors[iy * image.getWidth() + ix];
                if (color != MapColorMatcher.TRANSPARENT) pixel(canvas, x + ix, y + iy, color);
            }
        }
    }

    private static void drawImageClipped(MapCanvas canvas, int x, int y, BufferedImage image,
                                         int clipX, int clipY, int clipWidth, int clipHeight) {
        byte[] colors = MapColorMatcher.indices(image);
        for (int imageY = 0; imageY < image.getHeight(); imageY++) {
            int pixelY = y + imageY;
            if (pixelY < clipY || pixelY >= clipY + clipHeight) continue;
            for (int imageX = 0; imageX < image.getWidth(); imageX++) {
                int pixelX = x + imageX;
                if (pixelX < clipX || pixelX >= clipX + clipWidth) continue;
                byte color = colors[imageY * image.getWidth() + imageX];
                if (color != MapColorMatcher.TRANSPARENT) pixel(canvas, pixelX, pixelY, color);
            }
        }
    }

    private static void drawScribbleLine(MapCanvas canvas, int x1, int y1, int x2, int y2,
                                         byte color, int radius, long seed) {
        double dx = x2 - x1, dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 1) return;
        double normalX = -dy / length, normalY = dx / length;
        int steps = (int) Math.ceil(length * 2);
        long state = seed == 0 ? 1 : seed;
        for (int step = 0; step <= steps; step++) {
            double progress = (double) step / steps;
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            double wobble = ((state & 0x3) - 1.5) * 0.6;
            int centerX = (int) Math.round(x1 + dx * progress + normalX * wobble);
            int centerY = (int) Math.round(y1 + dy * progress + normalY * wobble);
            stampBrush(canvas, centerX, centerY, radius, color);
        }
    }

    private static void stampBrush(MapCanvas canvas, int centerX, int centerY, int radius, byte color) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy > radius * radius + radius) continue;
                pixel(canvas, centerX + dx, centerY + dy, color);
            }
        }
    }

    private static void drawHaloAndHatch(MapCanvas canvas, int gridX, int gridY, byte color) {
        final int originX = gridX * 24 + 4, originY = gridY * 24 + 4, size = 24;
        final int halo = 2;
        for (int dy = 1; dy < size - 1; dy++) {
            for (int dx = 1; dx < size - 1; dx++) {
                boolean onRing = dx < 1 + halo || dx >= size - 1 - halo
                        || dy < 1 + halo || dy >= size - 1 - halo;
                if (onRing) pixel(canvas, originX + dx, originY + dy, color);
            }
        }
        for (int dy = 1 + halo; dy < size - 1 - halo; dy++) {
            for (int dx = 1 + halo; dx < size - 1 - halo; dx++) {
                if (((dx - dy) % 5 + 5) % 5 == 0) pixel(canvas, originX + dx, originY + dy, color);
            }
        }
    }

    private static void pixel(MapCanvas canvas, int x, int y, byte color) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) canvas.setPixelColor(x, y, MapColorMatcher.color(color));
    }
}
