package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.BingoComponents;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Collect {@code count} of <em>any one</em> material in {@code items}. Completing a single member
 * satisfies the whole cell.
 *
 * <p>Both the container/chest UI and the map card show the single representative item
 * ({@link #getDisplayMaterial}); the map card additionally stamps a small "stack" badge on the cell so
 * it reads as "any one of a set". The cell's <em>name</em> is the family rather than that one variant —
 * derived from the shared affix of the members' enum names (see {@link #familyToken()}) so "all wools"
 * reads as "Any Wool", not "Any White Wool".
 */
public record OneOfTask(Set<Material> items, Material display, String name, int count, Dimension dimension)
        implements TaskData {

    public OneOfTask(Set<Material> items, Material display, String name, int count, Dimension dimension) {
        // Sort the set for deterministic icon/equality behaviour and freeze it.
        LinkedHashSet<Material> sorted = new LinkedHashSet<>();
        if (items != null) {
            items.stream().filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(Material::name))
                    .forEach(sorted::add);
        }
        if (sorted.isEmpty()) throw new IllegalArgumentException("OneOfTask requires at least one item");
        this.items = java.util.Collections.unmodifiableSet(sorted);
        this.display = display != null && sorted.contains(display) ? display : sorted.iterator().next();
        this.name = name == null || name.isBlank() ? null : name;
        this.count = Math.clamp(count, 1, 64);
        this.dimension = dimension == null ? Dimension.OVERWORLD : dimension;
    }

    @Override
    public TaskType getType() {
        return TaskType.ITEM_SET;
    }

    @Override
    public String objectiveId() {
        return "set:" + (name != null ? name : display.name());
    }

    /**
     * A lower-case family token shared by every member's enum name: the longest common run of
     * underscore-delimited segments taken from the end (suffix families like {@code *_WOOL → "wool"},
     * {@code *_STAINED_GLASS → "stained_glass"}) or, when no suffix is shared, from the start
     * ({@code RAW_* → "raw"}, {@code MUSIC_DISC_* → "music_disc"}). Empty when the members share no
     * affix at all (e.g. heads vs skulls), in which case {@link #getName()} falls back to the
     * representative item's own name. Used to look up a localized "Any &lt;family&gt;" label.
     */
    public String familyToken() {
        List<String[]> segs = new ArrayList<>(items.size());
        for (Material m : items) segs.add(segments(m));
        int suffix = commonRun(segs, true);
        int prefix = commonRun(segs, false);
        String[] first = segs.get(0);
        if (suffix >= prefix && suffix > 0) return join(first, first.length - suffix, first.length);
        if (prefix > 0) return join(first, 0, prefix);
        return "";
    }

    /**
     * Segments of a material's enum name, with vanilla synonyms folded together so families that mix
     * naming conventions still share an affix — notably {@code SKULL → HEAD}, so the mob-head set
     * ({@code SKELETON_SKULL}, {@code ZOMBIE_HEAD}, …) resolves to the "head" family instead of falling
     * back to one specific head's name.
     */
    private static String[] segments(Material m) {
        String[] raw = m.name().split("_");
        for (int i = 0; i < raw.length; i++) raw[i] = SEGMENT_SYNONYMS.getOrDefault(raw[i], raw[i]);
        return raw;
    }

    private static final Map<String, String> SEGMENT_SYNONYMS = Map.of("SKULL", "HEAD");

    /** Count of shared segments across every name, compared from the end ({@code fromEnd}) or start. */
    private static int commonRun(List<String[]> segs, boolean fromEnd) {
        int min = Integer.MAX_VALUE;
        for (String[] s : segs) min = Math.min(min, s.length);
        int run = 0;
        while (run < min) {
            String ref = seg(segs.get(0), run, fromEnd);
            boolean all = true;
            for (String[] s : segs) {
                if (!seg(s, run, fromEnd).equals(ref)) { all = false; break; }
            }
            if (!all) break;
            run++;
        }
        return run;
    }

    private static String seg(String[] arr, int i, boolean fromEnd) {
        return fromEnd ? arr[arr.length - 1 - i] : arr[i];
    }

    private static String join(String[] arr, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (sb.length() > 0) sb.append('_');
            sb.append(arr[i]);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    @Override
    public Component getName() {
        if (name != null) {
            return Component.text().color(NamedTextColor.YELLOW).append(Component.text(name)).build();
        }
        MessageService msg = MessageService.global();
        String token = familyToken();
        String familyKey = "task.family." + token;
        if (!token.isEmpty() && msg.has(familyKey)) {
            // Name the family, not one variant: "任意羊毛" / "Any Wool".
            return Component.text().color(NamedTextColor.YELLOW)
                    .append(Component.text(msg.tr("task.one_of_family", msg.tr(familyKey))))
                    .build();
        }
        // Irregular set with no family label: fall back to "Any: <representative item>".
        return Component.text().color(NamedTextColor.YELLOW)
                .append(Component.text(msg.tr("task.one_of_prefix")))
                .append(BingoComponents.itemName(display))
                .build();
    }

    @Override
    public Component[] getItemDescription() {
        return new Component[]{
                MessageService.global().component("task.one_of", items.size())
        };
    }

    @Override
    public Component getChatDescription() {
        return Component.text().append(getItemDescription()).build();
    }

    @Override
    public boolean shouldItemGlow() {
        return false;
    }

    /** The 16 vanilla dye colours, as the prefix segment(s) of a coloured block/item enum name. */
    private static final Set<String> DYE_COLORS = Set.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE",
            "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN",
            "RED", "BLACK");

    @Override
    public Material getDisplayMaterial(CardDisplayInfo context) {
        return iconMaterial();
    }

    /**
     * The member shown as the cell's representative icon (distinct from {@link #display}, which stays
     * the configured item so {@link #objectiveId()} is stable): a colour family always shows its
     * <em>white</em> variant for a consistent look, while a non-colour family shows one arbitrary
     * member picked deterministically from the set (so it's not always alphabetically first, yet never
     * flickers between renders of the same task).
     */
    private Material iconMaterial() {
        Material white = whiteVariant();
        if (white != null) return white;
        List<Material> sorted = new ArrayList<>(items);
        int seed = 0;
        for (Material m : items) seed = seed * 31 + m.name().hashCode(); // stable across JVM runs
        return sorted.get(Math.floorMod(seed, sorted.size()));
    }

    /**
     * When every member is a dye-colour variant of a single family (e.g. all {@code *_WOOL}), the
     * {@code WHITE_*} member of that family; {@code null} for non-colour families (planks, coral, raw
     * ores, music discs…) so they fall through to the deterministic pick.
     */
    private @Nullable Material whiteVariant() {
        List<String[]> segs = new ArrayList<>(items.size());
        for (Material m : items) segs.add(segments(m));
        int suffix = commonRun(segs, true);
        if (suffix == 0) return null;
        String[] first = segs.get(0);
        String family = join(first, first.length - suffix, first.length).toUpperCase(Locale.ROOT);
        // Every member's colour prefix (everything before the shared family suffix) must be a dye colour.
        for (String[] s : segs) {
            if (s.length <= suffix) return null; // a bare, un-prefixed base member → not a pure colour set
            if (!DYE_COLORS.contains(join(s, 0, s.length - suffix).toUpperCase(Locale.ROOT))) return null;
        }
        String whiteName = "WHITE_" + family;
        for (Material m : items) {
            if (m.name().equals(whiteName)) return m;
        }
        return null;
    }

    @Override
    public int getRequiredAmount() {
        return count;
    }

    @Override
    public TaskData setRequiredAmount(int newAmount) {
        return new OneOfTask(items, display, name, newAmount, dimension);
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return other instanceof OneOfTask set && items.equals(set.items);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OneOfTask that = (OneOfTask) o;
        return items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }
}
