package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.game.spatial.ReplicatedSpatialLayout;
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
    private final BattleBoxGeometry geometry;

    /** True once a team has reached 9 wool (or the match is otherwise settled); its players are frozen. */
    @Setter
    private boolean finished;

    public BattleBoxMatch(int copyIndex, ChampionshipTeam right, ChampionshipTeam left, BattleBoxConfig config) {
        this(copyIndex, right, left, new ReplicatedSpatialLayout<>(BattleBoxGeometry.from(config),
                BattleBoxLayout.GRID, config.getCopyCount()).geometry(copyIndex));
    }

    public BattleBoxMatch(int copyIndex, ChampionshipTeam right, ChampionshipTeam left,
                          BattleBoxGeometry geometry) {
        this.copyIndex = copyIndex;
        this.right = right;
        this.left = left;
        this.geometry = geometry;
    }

    public Location getRightSpawn() { return geometry.getRightSpawn(); }
    public Location getLeftSpawn() { return geometry.getLeftSpawn(); }
    public Location getRightPreSpawn() { return geometry.getRightPreSpawn(); }
    public Location getLeftPreSpawn() { return geometry.getLeftPreSpawn(); }
    public Location getSpectatorSpawn() { return geometry.getSpectatorSpawn(); }
    public List<Location> getPotionLocations() { return geometry.getPotionSpawns(); }

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
        return geometry.isInWool(point);
    }

    public boolean isInArea(Vector point) {
        return geometry.contains(point);
    }

    public BoundingBox getAreaBox() {
        return geometry.boundaryBox();
    }

    private World world() {
        return geometry.getRightSpawn() == null ? null : geometry.getRightSpawn().getWorld();
    }

    /** Counts blocks of each material inside this match's wool floor. */
    public HashMap<Material, Integer> countWool() {
        HashMap<Material, Integer> blockCount = new HashMap<>();
        World world = world();
        if (world == null) return blockCount;
        Vector woolMin = geometry.getWoolMin();
        Vector woolMax = geometry.getWoolMax();
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
        Vector woolMin = geometry.getWoolMin();
        Vector woolMax = geometry.getWoolMax();
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
