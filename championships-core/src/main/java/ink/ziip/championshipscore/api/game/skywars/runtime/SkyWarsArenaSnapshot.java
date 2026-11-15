package ink.ziip.championshipscore.api.game.skywars.runtime;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;

import ink.ziip.championshipscore.api.game.skywars.geometry.SkyWarsMapGeometry;

import org.bukkit.World;
import org.bukkit.entity.Player;

/** Restores the playable region without unloading the world occupied by the event audience. */
final class SkyWarsArenaSnapshot implements AutoCloseable {
    private final World world;
    private final BlockArrayClipboard blocks;

    private SkyWarsArenaSnapshot(World world, BlockArrayClipboard blocks) {
        this.world = world;
        this.blocks = blocks;
    }

    static SkyWarsArenaSnapshot capture(SkyWarsMapGeometry geometry) {
        World world = geometry.getBoundaryCenter().getWorld();
        var min = geometry.getBoundaryMin();
        var max = geometry.getBoundaryMax();
        var region =
                new CuboidRegion(
                        BukkitAdapter.adapt(world),
                        BlockVector3.at(min.getBlockX(), world.getMinHeight(), min.getBlockZ()),
                        BlockVector3.at(
                                max.getBlockX(), world.getMaxHeight() - 1, max.getBlockZ()));
        var blocks = new BlockArrayClipboard(region);
        blocks.setOrigin(region.getMinimumPoint());
        try (EditSession edit =
                WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            var copy = new ForwardExtentCopy(edit, region, blocks, region.getMinimumPoint());
            copy.setCopyingEntities(true);
            Operations.complete(copy);
            return new SkyWarsArenaSnapshot(world, blocks);
        } catch (Exception failure) {
            blocks.close();
            throw new IllegalStateException(
                    "Unable to capture SkyWars arena before the event", failure);
        }
    }

    void restore() {
        var region = blocks.getRegion();
        // Players retain their server entities and spectator ownership during the paste.
        for (var entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            var location = entity.getLocation();
            if (region.contains(
                    BlockVector3.at(
                            location.getBlockX(), location.getBlockY(), location.getBlockZ())))
                entity.remove();
        }
        try (EditSession edit =
                WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operations.complete(
                    new ClipboardHolder(blocks)
                            .createPaste(edit)
                            .to(blocks.getOrigin())
                            .ignoreAirBlocks(false)
                            .copyEntities(true)
                            .build());
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to restore occupied SkyWars arena", failure);
        }
    }

    @Override
    public void close() {
        blocks.close();
    }
}
