package ink.ziip.championshipscore.api.game.buildmart;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** A resource cuboid and the on-disk WorldEdit snapshot used to restore it. */
public record BuildMartMaterialZone(@NotNull UUID snapshotId, @NotNull Vector pos1, @NotNull Vector pos2) {
    public BuildMartMaterialZone {
        snapshotId = snapshotId == null ? UUID.randomUUID() : snapshotId;
        pos1 = pos1.clone();
        pos2 = pos2.clone();
    }

    public int minX() { return Math.min(pos1.getBlockX(), pos2.getBlockX()); }
    public int maxX() { return Math.max(pos1.getBlockX(), pos2.getBlockX()); }
    public int minY() { return Math.min(pos1.getBlockY(), pos2.getBlockY()); }
    public int maxY() { return Math.max(pos1.getBlockY(), pos2.getBlockY()); }
    public int minZ() { return Math.min(pos1.getBlockZ(), pos2.getBlockZ()); }
    public int maxZ() { return Math.max(pos1.getBlockZ(), pos2.getBlockZ()); }

    public long volume() {
        return ((long) maxX() - minX() + 1L)
                * ((long) maxY() - minY() + 1L)
                * ((long) maxZ() - minZ() + 1L);
    }
}
