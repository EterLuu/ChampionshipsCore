package ink.ziip.championshipscore.platform.bukkit.bingo.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Color;
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
    public static final int SLOT_SHADE_KEEP = 0xFF967452; // (150,116,82)
    private static final int SLOT_SHADE_DROP = 0xFF846446; // (132,100,70)
    private static final Color STAT_PANEL = new Color(88, 100, 120);
    private static final Color STAT_BORDER = new Color(48, 56, 70);
    private static final Color EVENT_PANEL = new Color(108, 96, 140);
    private static final Color EVENT_BORDER = new Color(56, 46, 80);
    private static final int BADGE = 16;
    private static final int BADGE_INSET = FRAME_BORDER;
    private static final int BADGE_X = BADGE_INSET - 2;
    private static final int BADGE_Y = CELL - BADGE - BADGE_INSET + 1;
    private static final int ITEM_NUDGE_X = 5;
    private static final int ITEM_NUDGE_Y = -5;
    /** Entity sprites sit a little farther right/down than item sprites in statistic cells. */
    private static final int ENTITY_NUDGE_X = 2;
    private static final int ENTITY_NUDGE_Y = 3;

    private static volatile boolean loaded;
    private static boolean failed;
    /** Statistic and event badges from the merged stat_event atlas. */
    private static final Map<String, Sprite> STAT_EVENT_SPRITES = new HashMap<>();
    private static final Map<String, Sprite> ITEMS = new HashMap<>();
    private static final Map<String, Sprite> ENTITIES = new HashMap<>();
    /** Per-effect potion sprites, keyed {@code <form-infix>/<effect>} e.g. {@code splash_potion/strength}. */
    private static final Map<String, Sprite> POTION_SPRITES = new HashMap<>();
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private static BufferedImage background;
    private static BufferedImage advancementFrameTask;
    private static BufferedImage advancementFrameGoal;
    private static BufferedImage advancementFrameChallenge;
    private static BufferedImage checkBadge;

    private TaskImageAtlas() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded || failed) return;
        try {
            try (InputStream atlasStream = resource(RES + "item_atlas.json")) {
                JsonObject atlas = JsonParser.parseReader(
                        new InputStreamReader(atlasStream, StandardCharsets.UTF_8)).getAsJsonObject();
                loadSection(atlas.getAsJsonObject("items"), ITEMS);
                loadSection(atlas.getAsJsonObject("entities"), ENTITIES);
            }
            try (InputStream statAtlasStream = resource(RES + "stat_event_atlas.json")) {
                JsonObject statAtlas = JsonParser.parseReader(
                        new InputStreamReader(statAtlasStream, StandardCharsets.UTF_8)).getAsJsonObject();
                loadSection(statAtlas.getAsJsonObject("badges"), STAT_EVENT_SPRITES);
            }
            try (InputStream potionAtlasStream = resource(RES + "potions_atlas.json")) {
                if (potionAtlasStream != null) {
                    JsonObject potionAtlas = JsonParser.parseReader(
                            new InputStreamReader(potionAtlasStream, StandardCharsets.UTF_8)).getAsJsonObject();
                    loadSection(potionAtlas.getAsJsonObject("potions"), POTION_SPRITES);
                }
            }
            Sprite harmingSplash = POTION_SPRITES.get("splash_potion/harming");
            if (harmingSplash != null) ITEMS.put("minecraft:harming_splash_potion", harmingSplash);
            Sprite healingPotion = POTION_SPRITES.get("potion/healing");
            if (healingPotion != null) ITEMS.put("minecraft:healing_potion", healingPotion);
            background = read(RES + "card_background.png");
            if (background != null) unifySlotShade(background);
            advancementFrameTask = read(RES + "advancement_frame_task.png");
            advancementFrameGoal = read(RES + "advancement_frame_goal.png");
            advancementFrameChallenge = read(RES + "advancement_frame_challenge.png");
            Sprite checkSprite = STAT_EVENT_SPRITES.get("check");
            checkBadge = checkSprite == null ? null
                    : checkSprite.sheet.getSubimage(checkSprite.x, checkSprite.y, checkSprite.w, checkSprite.h);
            if (checkBadge == null) checkBadge = createCheckBadge();
            loaded = true;
        } catch (Exception ex) {
            failed = true;
            java.util.logging.Logger.getLogger(TaskImageAtlas.class.getName())
                    .warning("Bingo task atlas failed to load: " + ex.getMessage());
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

        int offsetX = 0, offsetY = 0;
        if (section.has("offset")) {
            JsonArray offset = section.getAsJsonArray("offset");
            offsetX = offset.get(0).getAsInt();
            offsetY = offset.get(1).getAsInt();
        }

        BufferedImage sheet = read(RES + file);
        if (sheet == null) return;

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).getAsString();
            int x = offsetX + (i % colCount) * sizeX;
            int y = offsetY + (i / colCount) * sizeY;
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
        Sprite s = ITEMS.get(k);
        if (s == null) return null;
        return CACHE.computeIfAbsent(k, kk -> s.sheet.getSubimage(s.x, s.y, s.w, s.h));
    }

    private static @Nullable BufferedImage badgeImageFor(String name) {
        ensureLoaded();
        Sprite s = STAT_EVENT_SPRITES.get(name);
        if (s == null) return null;
        return CACHE.computeIfAbsent("b:" + name,
                kk -> s.sheet.getSubimage(s.x, s.y, s.w, s.h));
    }

    /** Resolves merged event badges first, then ordinary material sprites. */
    public static @Nullable BufferedImage eventBadgeImage(Key key) {
        if ("minecraft".equals(key.namespace())) {
            BufferedImage badge = badgeImageFor(key.value());
            if (badge != null) return badge;
        }
        return imageFor(key);
    }

    /**
     * Per-effect potion sprite for the map card, keyed by the potion form's atlas infix
     * ({@code potion}/{@code splash_potion}/{@code lingering_potion}) and the base effect
     * ({@code strength}, {@code night_vision}, …). {@code null} if that combination isn't bundled.
     */
    public static @Nullable BufferedImage potionImageFor(String formInfix, String effect) {
        ensureLoaded();
        String k = formInfix + "/" + effect;
        Sprite s = POTION_SPRITES.get(k);
        if (s == null) return null;
        return CACHE.computeIfAbsent("p:" + k, kk -> s.sheet.getSubimage(s.x, s.y, s.w, s.h));
    }

    public static @Nullable BufferedImage entityImageFor(Key key) {
        ensureLoaded();
        String k = key.asString();
        Sprite s = ENTITIES.get(k);
        if (s == null) s = ITEMS.get(k + "_spawn_egg");
        if (s == null) return null;
        Sprite resolved = s;
        return CACHE.computeIfAbsent("e:" + k,
                kk -> resolved.sheet.getSubimage(resolved.x, resolved.y, resolved.w, resolved.h));
    }

    public static @Nullable BufferedImage background() {
        ensureLoaded();
        return background;
    }

    public static BufferedImage statisticCell(@Nullable Key itemKey, Statistic stat) {
        ensureLoaded();
        return statisticCell(itemKey, statisticBadge(stat));
    }

    public static BufferedImage statisticCell(@Nullable Key itemKey, @Nullable BufferedImage badge) {
        ensureLoaded();
        BufferedImage item = null;
        boolean entitySprite = false;
        if (itemKey != null) {
            item = badge != null ? entityImageFor(itemKey) : null;
            entitySprite = item != null;
            if (item == null) {
                item = imageFor(itemKey);
            }
        }
        return TaskCellLayout.compose(STAT_PANEL, STAT_BORDER, item, entitySprite, badge);
    }

    public static BufferedImage eventCell(@Nullable Key itemKey, @Nullable BufferedImage badge) {
        ensureLoaded();
        BufferedImage item = null;
        boolean entitySprite = false;
        if (itemKey != null) {
            item = entityImageFor(itemKey);
            entitySprite = item != null;
            if (item == null) item = imageFor(itemKey);
        }
        return TaskCellLayout.compose(EVENT_PANEL, EVENT_BORDER, item, entitySprite, badge);
    }

    public static @Nullable BufferedImage checkBadge() {
        ensureLoaded();
        return checkBadge;
    }

    private static BufferedImage createCheckBadge() {
        BufferedImage image = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
        boolean[][] mask = {
                {false, false, false, false, false, false, false, false, true,  false, false},
                {false, false, false, false, false, false, false, true,  true,  true,  true },
                {false, false, true,  false, false, false, true,  true,  true,  true,  true },
                {true,  true,  true,  true,  false, true,  true,  true,  true,  true,  true },
                {false, true,  true,  true,  true,  true,  true,  true,  true,  false, false},
                {false, true,  true,  true,  true,  true,  true,  true,  false, false, false},
                {false, false, true,  true,  true,  true,  true,  false, false, false, false},
                {false, false, false, true,  true,  true,  false, false, false, false, false},
                {false, false, false, true,  true,  true,  false, false, false, false, false}
        };
        int originX = 7;
        int originY = 6;
        int outline = 0xFF173B25;
        int shadow = 0xFF2F8F48;
        int face = 0xFF58D36F;

        for (int y = 0; y < mask.length; y++) {
            for (int x = 0; x < mask[y].length; x++) {
                if (!mask[y][x]) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        image.setRGB(originX + x + dx, originY + y + dy, outline);
                    }
                }
            }
        }
        for (int y = 0; y < mask.length; y++) {
            for (int x = 0; x < mask[y].length; x++) {
                if (!mask[y][x]) continue;
                image.setRGB(originX + x, originY + y, shadow);
                if (y + 1 < mask.length && x + 1 < mask[y].length && mask[y + 1][x + 1]) {
                    image.setRGB(originX + x, originY + y, face);
                }
            }
        }
        return image;
    }

    private static @Nullable BufferedImage statisticBadge(Statistic stat) {
        if (stat == Statistic.USE_ITEM) return badgeImageFor("right_click");
        String name = switch (stat) {
            case BREAK_ITEM -> "item_broken";
            case CRAFT_ITEM -> "item_crafted";
            case KILL_ENTITY -> "kill_entity";
            default -> null;
        };
        if (name != null) return badgeImageFor(name);
        return switch (stat) {
            case JUMP,
                 STRIDER_ONE_CM,
                 MINECART_ONE_CM,
                 CLIMB_ONE_CM,
                 FLY_ONE_CM,
                 WALK_UNDER_WATER_ONE_CM,
                 BOAT_ONE_CM,
                 PIG_ONE_CM,
                 HORSE_ONE_CM,
                 CROUCH_ONE_CM,
                 AVIATE_ONE_CM,
                 WALK_ONE_CM,
                 WALK_ON_WATER_ONE_CM,
                 SWIM_ONE_CM,
                 FALL_ONE_CM,
                 SPRINT_ONE_CM,
                 HAPPY_GHAST_ONE_CM,
                 NAUTILUS_ONE_CM -> badgeImageFor("travel_arrow");
            case TALKED_TO_VILLAGER,
                 CAKE_SLICES_EATEN,
                 CAULDRON_FILLED,
                 BREWINGSTAND_INTERACTION,
                 BEACON_INTERACTION,
                 NOTEBLOCK_PLAYED,
                 CAULDRON_USED,
                 NOTEBLOCK_TUNED,
                 FLOWER_POTTED,
                 RECORD_PLAYED,
                 FURNACE_INTERACTION,
                 CRAFTING_TABLE_INTERACTION,
                 SLEEP_IN_BED,
                 INTERACT_WITH_BLAST_FURNACE,
                 INTERACT_WITH_SMOKER,
                 INTERACT_WITH_LECTERN,
                 INTERACT_WITH_CAMPFIRE,
                 INTERACT_WITH_CARTOGRAPHY_TABLE,
                 INTERACT_WITH_LOOM,
                 INTERACT_WITH_STONECUTTER,
                 BELL_RING,
                 INTERACT_WITH_ANVIL,
                 INTERACT_WITH_GRINDSTONE,
                 INTERACT_WITH_SMITHING_TABLE,
                 OPEN_BARREL,
                 CHEST_OPENED,
                 ENDERCHEST_OPENED,
                 SHULKER_BOX_OPENED,
                 TRAPPED_CHEST_TRIGGERED,
                 HOPPER_INSPECTED,
                 DROPPER_INSPECTED,
                 DISPENSER_INSPECTED -> badgeImageFor("right_click");
            case TRADED_WITH_VILLAGER -> imageFor(Material.EMERALD.key());
            case TARGET_HIT -> imageFor(Material.BOW.key());
            case FISH_CAUGHT -> imageFor(Material.FISHING_ROD.key());
            case ARMOR_CLEANED, BANNER_CLEANED, CLEAN_SHULKER_BOX -> imageFor(Material.CAULDRON.key());
            case RAID_TRIGGER -> imageFor(Material.OMINOUS_BOTTLE.key());
            case RAID_WIN -> checkBadge();
            case DAMAGE_DEALT,
                 DAMAGE_TAKEN,
                 DAMAGE_DEALT_RESISTED,
                 DAMAGE_RESISTED,
                 DAMAGE_BLOCKED_BY_SHIELD -> badgeImageFor("half_heart");
            case DAMAGE_ABSORBED,
                 DAMAGE_DEALT_ABSORBED -> badgeImageFor("half_absorption_heart");
            default -> null;
        };
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
