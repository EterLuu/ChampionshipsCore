package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/** A color floor stage's seven deadlines and layouts, independent of Bukkit scheduling and world mutation. */
final class RiptideColorFloorRun {
    static final int INTRO_TICKS = 30;
    enum Theme { COPPER, WOOD, TERRACOTTA, STONE, ORE, LOG, NETHER }
    enum Pattern { RINGS, PATCHES, RANDOM, HORIZONTAL, VERTICAL }
    private static final List<String> COLORS = List.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE",
            "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE",
            "BROWN", "GREEN", "RED", "BLACK");
    private final int width;
    private final int length;
    private final Random random;
    private final Theme theme;
    private final List<Material> palette;
    private final List<Integer> durations;
    private final List<Material> authoredFloor;
    private boolean presetPattern;
    private List<Material> floor;
    private Material target;
    private Pattern pattern;
    private int round;
    private int remainingTicks;
    private int introRemaining = INTRO_TICKS;

    RiptideColorFloorRun(int width, int length, List<Integer> roundTicks, Random random, Theme theme) {
        this(width, length, roundTicks, random, theme, List.of(), theme == Theme.COPPER ? 8 : 6);
    }
    RiptideColorFloorRun(int width, int length, List<Integer> roundTicks, Random random, Theme theme, List<Material> authoredFloor) {
        this(width, length, roundTicks, random, theme, authoredFloor,
                authoredFloor.isEmpty() ? (theme == Theme.COPPER ? 8 : 6) : (int) authoredFloor.stream().distinct().count());
    }
    RiptideColorFloorRun(int width, int length, List<Integer> roundTicks, Random random, Theme theme,
                         List<Material> authoredFloor, int materialCount) {
        if (materialCount < 2 || materialCount > 8) throw new IllegalArgumentException("invalid palette size");
        this.authoredFloor = List.copyOf(authoredFloor);
        if (!this.authoredFloor.isEmpty()) RiptideBlueprint.validateFloor(this.authoredFloor, width, length);
        if (width < 3 || length < 3 || width % 2 == 0 || length % 2 == 0 || roundTicks.size() != 7 || roundTicks.stream().anyMatch(t -> t == null || t <= 0))
            throw new IllegalArgumentException("invalid color floor dimensions or duration");
        this.width = width;
        this.length = length;
        this.random = random;
        this.theme = theme;
        var choices = new ArrayList<>(materials(theme));
        Collections.shuffle(choices, random);
        var selected = new ArrayList<>(authoredFloor.stream().distinct().limit(materialCount).toList());
        for (var material : choices) if (selected.size() < materialCount && !selected.contains(material)) selected.add(material);
        palette = List.copyOf(selected);
        durations = List.copyOf(roundTicks);
        prepareRound();
    }

    private void prepareRound() {
        // Each deadline independently chooses between a random layout and a preset layout.
        presetPattern = random.nextBoolean();
        if (presetPattern) {
            pattern = authoredFloor.isEmpty() ? chooseFixedPattern(random) : Pattern.RINGS;
            floor = authoredFloor.isEmpty() ? layout(width, length, palette, pattern, random) : stagedAuthoredFloor();
        } else {
            pattern = Pattern.RANDOM;
            floor = layout(width, length, palette, pattern, random);
        }
        List<Material> targets = new ArrayList<>(palette);
        targets.remove(target); // Every round asks for a different block.
        target = targets.get(random.nextInt(targets.size()));
        remainingTicks = durations.get(round);
    }

    private static Pattern chooseFixedPattern(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> Pattern.RINGS;
            case 1 -> Pattern.PATCHES;
            case 2 -> Pattern.HORIZONTAL;
            default -> Pattern.VERTICAL;
        };
    }

    private List<Material> stagedAuthoredFloor() {
        var original = authoredFloor.stream().distinct().toList();
        if (original.equals(palette)) return authoredFloor;
        var cells = new ArrayList<>(authoredFloor.stream()
                .map(m -> palette.get(original.indexOf(m) % palette.size())).toList());
        // Preserve authored regions where possible, adding missing stage materials to distinct cells.
        for (int i = 0; i < palette.size(); i++) cells.set(i, palette.get(i));
        return List.copyOf(cells);
    }

    /** Returns true exactly once per deadline; the caller evaluates everyone before advancing. */
    boolean tick() {
        if (introRemaining > 0) { introRemaining--; return false; }
        return remainingTicks > 0 && --remainingTicks == 0;
    }

    boolean preparing() { return introRemaining > 0; }

    boolean advance() {
        if (remainingTicks != 0) throw new IllegalStateException("round has not ended");
        if (round == durations.size() - 1) return false;
        round++;
        prepareRound();
        return true;
    }

    /** Feet projected onto a real platform cell; normal jumps above it are allowed. */
    boolean matches(Location feet, RiptideCourseGeometry geometry, int step) {
        if (feet.getWorld() == null || !feet.getWorld().equals(geometry.centerAt(step).getWorld())
                || feet.getY() < geometry.floorY() + 1D - 0.05D
                || feet.getY() > geometry.floorY() + 2.5D) return false;
        for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
            if (floor.get(z * width + x) == target && RiptideCourseGeometry.overlapsCell(feet,
                    geometry.blockX(step + z - length / 2, x - width / 2),
                    geometry.blockZ(step + z - length / 2, x - width / 2))) return true;
        }
        return false;
    }

    static List<Material> layout(int width, int length, List<Material> palette, Pattern pattern, Random random) {
        var colors = new ArrayList<>(palette);
        Collections.shuffle(colors, random);
        int count = colors.size();
        var radii = new TreeSet<Integer>();
        for (int z = 0; z < length; z++) for (int x = 0; x < width; x++)
            radii.add(square(x - width / 2) + square(z - length / 2));
        var orderedRadii = new ArrayList<>(radii);
        List<Material> cells = new ArrayList<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = switch (pattern) {
                    case RINGS -> Collections.binarySearch(orderedRadii,
                            square(x - width / 2) + square(z - length / 2)) * count / orderedRadii.size();
                    case PATCHES -> (z * 2 / length) * 3 + x * 3 / width;
                    case HORIZONTAL -> z % count;
                    case VERTICAL -> x % count;
                    case RANDOM -> (z * width + x) % count;
                };
                cells.add(colors.get(index % count));
            }
        }
        // Small custom rafts may not fit six rings/stripes. Never offer an absent answer.
        if (!cells.containsAll(colors)) {
            cells.clear();
            for (int i = 0; i < width * length; i++) cells.add(colors.get(i % count));
            Collections.shuffle(cells, random);
        } else if (pattern == Pattern.RANDOM) Collections.shuffle(cells, random);
        return List.copyOf(cells);
    }

    private static int square(int n) { return n * n; }

    static List<Material> materials(Theme theme) {
        return switch (theme) {
            case ORE -> List.of(Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE,
                    Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE);
            case LOG -> List.of(Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
                    Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG);
            case NETHER -> List.of(Material.NETHERRACK, Material.SOUL_SOIL, Material.BASALT,
                    Material.BLACKSTONE, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE);
            case TERRACOTTA -> COLORS.stream().map(color -> Material.valueOf(color + "_TERRACOTTA")).toList();
            case COPPER -> List.of(Material.WAXED_COPPER_BLOCK, Material.WAXED_EXPOSED_COPPER,
                    Material.WAXED_WEATHERED_COPPER, Material.WAXED_OXIDIZED_COPPER,
                    Material.WAXED_CUT_COPPER, Material.WAXED_EXPOSED_CUT_COPPER,
                    Material.WAXED_WEATHERED_CUT_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER,
                    Material.WAXED_CHISELED_COPPER, Material.WAXED_EXPOSED_CHISELED_COPPER,
                    Material.WAXED_WEATHERED_CHISELED_COPPER, Material.WAXED_OXIDIZED_CHISELED_COPPER);
            case WOOD -> List.of(Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
                    Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
                    Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS,
                    Material.CRIMSON_PLANKS, Material.WARPED_PLANKS);
            case STONE -> List.of(Material.STONE, Material.ANDESITE, Material.POLISHED_ANDESITE,
                    Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
                    Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.MOSSY_STONE_BRICKS);
        };
    }

    List<Material> floor() { return floor; }
    Material target() { return target; }
    Theme theme() { return theme; }
    Pattern pattern() { return pattern; }
    int roundCount() { return durations.size(); }
    int roundNumber() { return round + 1; }
    int remainingTicks() { return remainingTicks; }
    List<Integer> durations() { return durations; }
    boolean presetPattern() { return presetPattern; }
}
