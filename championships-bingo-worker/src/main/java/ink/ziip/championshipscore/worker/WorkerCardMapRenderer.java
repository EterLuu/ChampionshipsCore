package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.map.MapColorMatcher;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        drawCompletedLines(canvas, completions, width, offset, winner == null ? viewerTeam : winner);
        lastSignature = signature;
    }

    private void drawTask(MapCanvas canvas, BingoTaskSpec task, int gridX, int gridY) {
        int x = gridX * 24 + 4;
        int y = gridY * 24 + 4;
        BufferedImage image;
        if ("statistic".equals(task.taskType())) {
            Key subject = subjectKey(task);
            Statistic statistic = enumValue(Statistic.class, task.attributes().get("statistic"));
            image = statistic == null ? null : TaskImageAtlas.statisticCell(subject, statistic);
        } else if ("potion".equals(task.taskType())) {
            Material material = material(task.attributes().get("material"));
            image = TaskImageAtlas.potionImageFor(potionInfix(material), task.attributes().get("effect"));
            if (image == null) image = atlas(material);
        } else {
            Material icon = icon(task);
            if ("advancement".equals(task.taskType())) {
                Advancement advancement = advancement(task.attributes().get("key"));
                if (advancement != null && advancement.getDisplay() != null) {
                    BufferedImage frame = TaskImageAtlas.advancementFrame(advancement.getDisplay().frame());
                    if (frame != null) drawImage(canvas, x - 1, y - 1, frame);
                }
            }
            image = atlas(icon);
        }
        if (image != null) drawImage(canvas, x + 1, y + 1, image);
        int amount = displayAmount(task);
        if (amount > 1 || "statistic".equals(task.taskType())) {
            canvas.drawText(gridX * 24 + 17, gridY * 24 + 20, MinecraftFont.Font, "§58;" + amount);
        }
    }

    private void drawBorders(MapCanvas canvas, int gridX, int gridY, List<Integer> completedTeams) {
        if (completedTeams.isEmpty()) return;
        int ox = gridX * 24 + 5;
        int oy = gridY * 24 + 5;
        int segmentLength = Math.max(1, 22 / completedTeams.size());
        for (int index = 0; index < completedTeams.size(); index++) {
            byte color = teamColor(completedTeams.get(index));
            int start = index * segmentLength;
            int end = index == completedTeams.size() - 1 ? 22 : Math.min(22, start + segmentLength);
            for (int point = start; point < end; point++) {
                pixel(canvas, ox + point, oy, color);
                pixel(canvas, ox + point, oy + 21, color);
                pixel(canvas, ox, oy + point, color);
                pixel(canvas, ox + 21, oy + point, color);
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
            line(canvas, (first % width + offset) * 24 + 16, (first / width + offset) * 24 + 16,
                    (last % width + offset) * 24 + 16, (last / width + offset) * 24 + 16, color);
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

    private static Material icon(BingoTaskSpec task) {
        return switch (task.taskType()) {
            case "item", "potion" -> material(task.attributes().get("material"));
            case "item_set" -> material(task.attributes().getOrDefault("materials", "PAPER").split(",")[0]);
            case "advancement" -> {
                Advancement advancement = advancement(task.attributes().get("key"));
                yield advancement == null || advancement.getDisplay() == null
                        ? Material.FILLED_MAP : advancement.getDisplay().icon().getType();
            }
            default -> material(task.attributes().get("material"));
        };
    }

    private static Key subjectKey(BingoTaskSpec task) {
        Material material = material(task.attributes().get("material"));
        if (material != Material.PAPER || task.attributes().containsKey("material")) return material.key();
        EntityType entity = enumValue(EntityType.class, task.attributes().get("entity"));
        return entity == null ? null : entity.key();
    }

    private static BufferedImage atlas(Material material) {
        return material == null ? null : TaskImageAtlas.imageFor(material.key());
    }

    private static Material material(String name) {
        Material material = name == null ? null : Material.matchMaterial(name.replace("MINECRAFT:", ""));
        return material == null ? Material.PAPER : material;
    }

    private static Advancement advancement(String raw) {
        NamespacedKey key = raw == null ? null : NamespacedKey.fromString(raw);
        return key == null ? null : Bukkit.getAdvancement(key);
    }

    private static String potionInfix(Material material) {
        return switch (material) {
            case SPLASH_POTION -> "splash_potion";
            case LINGERING_POTION -> "lingering_potion";
            default -> "potion";
        };
    }

    private static int displayAmount(BingoTaskSpec task) {
        String raw = task.attributes().getOrDefault("count", task.attributes().getOrDefault("target", "1"));
        try {
            int value = Integer.parseInt(raw);
            Statistic statistic = enumValue(Statistic.class, task.attributes().get("statistic"));
            if (statistic != null && statistic.name().endsWith("_ONE_CM")) value /= 100;
            return Math.max(1, value);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
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

    private static void line(MapCanvas canvas, int x0, int y0, int x1, int y1, byte color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) {
                pixel(canvas, x0 + ox, y0 + oy, color);
            }
            if (x0 == x1 && y0 == y1) return;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static void pixel(MapCanvas canvas, int x, int y, byte color) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) canvas.setPixelColor(x, y, MapColorMatcher.color(color));
    }
}
