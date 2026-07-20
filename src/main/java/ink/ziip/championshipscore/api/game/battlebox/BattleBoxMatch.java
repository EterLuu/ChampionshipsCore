package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * One team-vs-team Battle Box match running in a single stamped arena copy. All geometry is the configured
 * copy-0 template shifted onto this copy's slot via {@link BattleBoxLayout#delta(int)}, so a match's spawns,
 * wool floor and bounds always line up with the arena physically pasted for its {@code copyIndex}. Block
 * operations (counting/resetting the centre wool) live here since they are geometry-bound; scoring stays in
 * {@link BattleBoxArea} which owns the point tallies.
 */
@Getter
public class BattleBoxMatch {
    private final int copyIndex;
    private final ChampionshipTeam right;
    private final ChampionshipTeam left;

    private final Location rightSpawn;
    private final Location leftSpawn;
    private final Location rightPreSpawn;
    private final Location leftPreSpawn;
    private final Location spectatorSpawn;

    /** Inclusive wool-floor corners (where blocks may be placed/broken and wool is counted). */
    private final Vector woolMin;
    private final Vector woolMax;
    /** Inclusive overall arena bounds for this copy (in-bounds / item cleanup). */
    private final Vector areaMin;
    private final Vector areaMax;

    private final List<Location> potionLocations = new ArrayList<>();

    /** True once a team has reached 9 wool (or the match is otherwise settled); its players are frozen. */
    @Setter
    private boolean finished;

    public BattleBoxMatch(int copyIndex, ChampionshipTeam right, ChampionshipTeam left, BattleBoxConfig config) {
        this.copyIndex = copyIndex;
        this.right = right;
        this.left = left;
        Vector d = BattleBoxLayout.delta(copyIndex);

        this.rightSpawn = shift(config.getRightSpawnPoint(), d);
        this.leftSpawn = shift(config.getLeftSpawnPoint(), d);
        this.rightPreSpawn = shift(config.getRightPreSpawnPoint(), d);
        this.leftPreSpawn = shift(config.getLeftPreSpawnPoint(), d);
        this.spectatorSpawn = shift(config.getSpectatorSpawnPoint(), d);

        this.woolMin = Vector.getMinimum(config.getWoolPos1(), config.getWoolPos2()).add(d);
        this.woolMax = Vector.getMaximum(config.getWoolPos1(), config.getWoolPos2()).add(d);
        this.areaMin = Vector.getMinimum(config.getAreaPos1(), config.getAreaPos2()).add(d);
        this.areaMax = Vector.getMaximum(config.getAreaPos1(), config.getAreaPos2()).add(d);

        if (config.getPotionSpawnPoints() != null) {
            for (String raw : config.getPotionSpawnPoints()) {
                Location location = Utils.getLocation(raw);
                if (location != null) potionLocations.add(location.add(d));
            }
        }
    }

    @Nullable
    private static Location shift(@Nullable Location location, Vector delta) {
        return location == null ? null : location.clone().add(delta);
    }

    /** True when {@code player} belongs to either team in this match. */
    public boolean contains(Player player) {
        UUID uuid = player.getUniqueId();
        return right.getMembers().contains(uuid) || left.getMembers().contains(uuid);
    }

    /** The opposing team of {@code team} in this match, or {@code null} if {@code team} is not in it. */
    @Nullable
    public ChampionshipTeam rivalOf(ChampionshipTeam team) {
        if (team == null) return null;
        if (team.equals(right)) return left;
        if (team.equals(left)) return right;
        return null;
    }

    public boolean isInWool(Vector point) {
        return point.isInAABB(woolMin, woolMax);
    }

    public boolean isInArea(Vector point) {
        return point.isInAABB(areaMin, areaMax);
    }

    public BoundingBox getAreaBox() {
        return BoundingBox.of(areaMin, areaMax.clone().add(new Vector(1, 1, 1)));
    }

    private World world() {
        return rightSpawn == null ? null : rightSpawn.getWorld();
    }

    /** Counts blocks of each material inside this match's wool floor. */
    public HashMap<Material, Integer> countWool() {
        HashMap<Material, Integer> blockCount = new HashMap<>();
        World world = world();
        if (world == null) return blockCount;
        for (int x = woolMin.getBlockX(); x <= woolMax.getBlockX(); x++) {
            for (int y = woolMin.getBlockY(); y <= woolMax.getBlockY(); y++) {
                for (int z = woolMin.getBlockZ(); z <= woolMax.getBlockZ(); z++) {
                    Material material = world.getBlockAt(x, y, z).getType();
                    blockCount.put(material, blockCount.getOrDefault(material, 0) + 1);
                }
            }
        }
        return blockCount;
    }

    /** Fills this match's wool floor with {@code material}. */
    public void resetWool(Material material) {
        World world = world();
        if (world == null) return;
        for (int x = woolMin.getBlockX(); x <= woolMax.getBlockX(); x++) {
            for (int y = woolMin.getBlockY(); y <= woolMax.getBlockY(); y++) {
                for (int z = woolMin.getBlockZ(); z <= woolMax.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(material);
                    BlockState state = block.getState();
                    state.setType(material);
                    state.update();
                }
            }
        }
    }

    /** Resets the centre to a neutral colour avoiding both teams' wool colours, like the original single match. */
    public void resetCenterWool() {
        Material material = Material.WHITE_WOOL;
        if (right.getWool().getType() != material && left.getWool().getType() != material) {
            resetWool(material);
            return;
        }
        for (String color : Utils.getColorNames()) {
            if (!color.equalsIgnoreCase(right.getColorName()) && !color.equalsIgnoreCase(left.getColorName())) {
                Material candidate = Material.getMaterial(color.toUpperCase() + "_WOOL");
                if (candidate != null) {
                    material = candidate;
                    break;
                }
            }
        }
        resetWool(material);
    }
}
