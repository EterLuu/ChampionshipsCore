package ink.ziip.championshipscore.integration.worldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.RegionSelector;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class WorldEditManager extends BaseManager {
    private final WorldEdit worldEdit;

    public WorldEditManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        worldEdit = WorldEdit.getInstance();
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {

    }

    public Vector[] getPlayerSelection(@NotNull Player player, boolean blockVector) {
        Vector[] vectors = new Vector[2];
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
        RegionSelector selector = worldEdit.getSessionManager().get(bukkitPlayer).getRegionSelector(bukkitPlayer.getWorld());
        BlockVector3 v1 = selector.getRegion().getMinimumPoint();
        BlockVector3 v2 = selector.getRegion().getMaximumPoint();
        if (blockVector) {
            vectors[0] = new Vector(v1.x(), v1.y(), v1.z());
            vectors[1] = new Vector(v2.x(), v2.y(), v2.z());
        } else {
            vectors[0] = new Vector(v1.x() + 1, v1.y(), v1.z() + 1);
            vectors[1] = new Vector(v2.x(), v2.y(), v2.z());
        }
        return vectors;
    }
}
