package ink.ziip.championshipscore.api.game.bingo.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the bundled task-image atlas (a 22x22 sprite sheet + an entity sheet + overlays) once and
 * serves per-item textures for the map renderer. Resources live under {@code bingo/taskimages/} in the
 * jar. All sprites are a uniform 22x22, so there is no flat/block size distinction.
 */
public final class TaskImageAtlas {
    private record Sprite(BufferedImage sheet, int x, int y, int w, int h) {
    }

    private static final String RES = "bingo/taskimages/";

    /** Pixel size of one card cell. */
    private static final int CELL = 24;
    private static final int FRAME_BORDER = 2;
    private static final int SLOT_SHADE_KEEP = 0xFF967452; // (150,116,82)
    private static final int SLOT_SHADE_DROP = 0xFF846446; // (132,100,70)
    private static final Color STAT_PANEL = new Color(88, 100, 120);
    private static final Color STAT_BORDER = new Color(48, 56, 70);
    private static final int BADGE = 16;
    private static final int BADGE_INSET = FRAME_BORDER;
    private static final int BADGE_X = BADGE_INSET - 2;
    private static final int BADGE_Y = CELL - BADGE - BADGE_INSET + 1;
    private static final int ITEM_NUDGE_X = 5;
    private static final int ITEM_NUDGE_Y = -5;

    private static volatile boolean loaded;
    private static boolean failed;
    private static final Map<String, Sprite> SPRITES = new HashMap<>();
    private static final Map<String, Sprite> ENTITIES = new HashMap<>();
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private static BufferedImage background;
    private static final Map<String, BufferedImage> statisticBadges = new HashMap<>();
    private static BufferedImage advancementFrameTask;
    private static BufferedImage advancementFrameGoal;
    private static BufferedImage advancementFrameChallenge;

    private TaskImageAtlas() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded || failed) return;
        try {
            try (InputStream atlasStream = resource(RES + "item_atlas.json")) {
                JsonObject atlas = JsonParser.parseReader(
                        new InputStreamReader(atlasStream, StandardCharsets.UTF_8)).getAsJsonObject();
                loadSection(atlas.getAsJsonObject("sprites"), SPRITES);
                loadSection(atlas.getAsJsonObject("entities"), ENTITIES);
            }
            background = read(RES + "card_background.png");
            if (background != null) unifySlotShade(background);
            for (String name : new String[]{"block_mined", "item_broken", "item_crafted", "item_used",
                    "item_picked_up", "item_dropped", "kill_entity", "entity_killed_by"}) {
                BufferedImage badge = read(RES + "statistic_badge/" + name + ".png");
                if (badge != null) statisticBadges.put(name, badge);
            }
            advancementFrameTask = read(RES + "advancement_frame_task.png");
            advancementFrameGoal = read(RES + "advancement_frame_goal.png");
            advancementFrameChallenge = read(RES + "advancement_frame_challenge.png");
            loaded = true;
            Bukkit.getLogger().info("[Bingo] Loaded " + SPRITES.size() + " task images for map rendering.");
        } catch (Exception ex) {
            failed = true;
            Bukkit.getLogger().warning("[Bingo] Failed to load task image atlas: " + ex.getMessage());
        }
    }

    private static void unifySlotShade(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getRGB(x, y) == SLOT_SHADE_DROP) img.setRGB(x, y, SLOT_SHADE_KEEP);
            }
        }
    }

    private static void loadSection(JsonObject section, Map<String, Sprite> dest) throws Exception {
        if (section == null) return;
        String file = section.get("file").getAsString();
        int rows = section.has("rows") ? section.get("rows").getAsInt() : 1;
        JsonArray sizeVec = section.getAsJsonArray("texture_size");
        int sizeX = sizeVec.get(0).getAsInt();
        int sizeY = sizeVec.get(1).getAsInt();
        JsonArray names = section.getAsJsonArray("names");
        int colCount = names.size() / rows + 1;

        BufferedImage sheet = read(RES + file);
        if (sheet == null) return;

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).getAsString();
            int x = (i % colCount) * sizeX;
            int y = (i / colCount) * sizeY;
            if (x + sizeX > sheet.getWidth() || y + sizeY > sheet.getHeight()) continue;
            dest.put(name, new Sprite(sheet, x, y, sizeX, sizeY));
        }
    }

    private static @Nullable BufferedImage read(String path) throws Exception {
        try (InputStream in = resource(path)) {
            return in == null ? null : ImageIO.read(in);
        }
    }

    private static @Nullable InputStream resource(String path) {
        return TaskImageAtlas.class.getClassLoader().getResourceAsStream(path);
    }

    public static @Nullable BufferedImage imageFor(Key key) {
        ensureLoaded();
        String k = key.asString();
        Sprite s = SPRITES.get(k);
        if (s == null) return null;
        return CACHE.computeIfAbsent(k, kk -> s.sheet.getSubimage(s.x, s.y, s.w, s.h));
    }

    public static @Nullable BufferedImage composite(java.util.List<Key> members, Key fallback) {
        ensureLoaded();
        if (members == null || members.isEmpty()) return imageFor(fallback);

        int parts = Math.min(members.size(), 4);
        StringBuilder ck = new StringBuilder("c:").append(fallback == null ? "" : fallback.asString()).append('|');
        for (int i = 0; i < parts; i++) ck.append(members.get(i).asString()).append(',');
        String cacheKey = ck.toString();
        BufferedImage cached = CACHE.get(cacheKey);
        if (cached != null) return cached;

        BufferedImage base = fallback == null ? null : imageFor(fallback);
        BufferedImage[] img = new BufferedImage[parts];
        int w = -1, h = -1, resolved = 0;
        for (int i = 0; i < parts; i++) {
            BufferedImage m = imageFor(members.get(i));
            if (m != null) resolved++;
            else m = base;
            img[i] = m;
            if (m != null && w < 0) {
                w = m.getWidth();
                h = m.getHeight();
            }
        }
        if (w < 0) return base;
        if (parts == 1 || resolved < 2) return parts == 1 ? img[0] : base;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int w2 = w / 2, h2 = h / 2;
        switch (parts) {
            case 2 -> {
                copyRegion(out, img[0], 0, 0, w2, h);
                copyRegion(out, img[1], w2, 0, w, h);
            }
            case 3 -> {
                copyRegion(out, img[0], 0, 0, w2, h2);
                copyRegion(out, img[1], w2, 0, w, h2);
                copyRegion(out, img[2], 0, h2, w, h);
            }
            default -> {
                copyRegion(out, img[0], 0, 0, w2, h2);
                copyRegion(out, img[1], w2, 0, w, h2);
                copyRegion(out, img[2], 0, h2, w2, h);
                copyRegion(out, img[3], w2, h2, w, h);
            }
        }
        CACHE.put(cacheKey, out);
        return out;
    }

    private static void copyRegion(BufferedImage out, @Nullable BufferedImage src,
                                   int x0, int y0, int x1, int y1) {
        if (src == null) return;
        int sw = src.getWidth(), sh = src.getHeight();
        for (int y = y0; y < y1 && y < sh && y < out.getHeight(); y++) {
            for (int x = x0; x < x1 && x < sw && x < out.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(x, y));
            }
        }
    }

    public static @Nullable BufferedImage entityImageFor(Key key) {
        ensureLoaded();
        String k = key.asString();
        Sprite s = ENTITIES.get(k);
        if (s == null) return null;
        return CACHE.computeIfAbsent("e:" + k, kk -> s.sheet.getSubimage(s.x, s.y, s.w, s.h));
    }

    public static @Nullable BufferedImage background() {
        ensureLoaded();
        return background;
    }

    public static BufferedImage statisticCell(@Nullable Key itemKey, Statistic stat) {
        ensureLoaded();
        BufferedImage tile = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();

        int panel = CELL - 2;
        g.setColor(STAT_PANEL);
        g.fillRect(1, 1, panel, panel);
        g.setColor(STAT_BORDER);
        g.drawRect(1, 1, panel - 1, panel - 1);

        int inner = CELL - 2 * FRAME_BORDER;
        g.setClip(FRAME_BORDER, FRAME_BORDER, inner, inner);

        BufferedImage badge = statisticBadge(stat);

        BufferedImage item = null;
        if (itemKey != null) {
            item = badge != null ? entityImageFor(itemKey) : null;
            if (item == null) {
                item = imageFor(itemKey);
            }
        }
        if (item != null) {
            // All sprites are 22x22; draw at the same +1 offset used by normal cells. Nudged top-right
            // only when a badge shares the cell, otherwise left centred.
            int nudgeX = badge != null ? ITEM_NUDGE_X : 0;
            int nudgeY = badge != null ? ITEM_NUDGE_Y : 0;
            g.drawImage(item, 1 + nudgeX, 1 + nudgeY, null);
        }

        if (badge != null) {
            g.drawImage(badge, BADGE_X, BADGE_Y, null);
        }

        g.dispose();
        return tile;
    }

    private static @Nullable BufferedImage statisticBadge(Statistic stat) {
        String name = switch (stat) {
            case MINE_BLOCK -> "block_mined";
            case BREAK_ITEM -> "item_broken";
            case CRAFT_ITEM -> "item_crafted";
            case USE_ITEM -> "item_used";
            case PICKUP -> "item_picked_up";
            case DROP -> "item_dropped";
            case KILL_ENTITY -> "kill_entity";
            case ENTITY_KILLED_BY -> "entity_killed_by";
            default -> null;
        };
        return name == null ? null : statisticBadges.get(name);
    }

    public static @Nullable BufferedImage advancementFrame(@Nullable AdvancementDisplay.Frame type) {
        ensureLoaded();
        if (type == null) return advancementFrameTask;
        return switch (type) {
            case CHALLENGE -> advancementFrameChallenge;
            case GOAL -> advancementFrameGoal;
            default -> advancementFrameTask;
        };
    }
}
