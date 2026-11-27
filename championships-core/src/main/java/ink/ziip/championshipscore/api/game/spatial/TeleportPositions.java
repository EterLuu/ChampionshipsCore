package ink.ziip.championshipscore.api.game.spatial;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Position geometry used by lobby returns, map capture and batch teleports. */
public final class TeleportPositions {
    private TeleportPositions() {}

    /**
     * Horizontal scatter radius (blocks) around the lobby spawn used to spread returning players
     * apart.
     */
    private static final double LOBBY_SCATTER_RADIUS = 5.0D;

    /**
     * Lobby spawn scattered horizontally around the configured centre for one player. The player's
     * UUID selects a stable golden-angle offset, so a crowd returning from a finished match spreads
     * over the surrounding floor instead of stacking on one point and pushing each other apart.
     * Offsets that land on walls or off the floor shrink progressively back to the exact centre.
     */
    public static Location getScatteredLobbyLocation(
            @Nullable Location lobby, @NotNull Player player) {
        if (lobby == null || lobby.getWorld() == null) return lobby;
        int hash = player.getUniqueId().hashCode();
        double angle = ((hash & 0xFFFF) / 65536.0D) * Math.PI * 2.0D;
        double radius = LOBBY_SCATTER_RADIUS * Math.sqrt(((hash >>> 16) & 0xFFFF) / 65536.0D);
        for (double scale = 1.0D; scale >= 0.2D; scale *= 0.5D) {
            Location candidate = lobby.clone();
            candidate.setX(lobby.getX() + Math.cos(angle) * radius * scale);
            candidate.setZ(lobby.getZ() + Math.sin(angle) * radius * scale);
            if (isSafeLobbySpot(candidate)) return candidate;
        }
        return lobby;
    }

    /**
     * Returns a deterministic nearby slot for a batch teleport. A shared target is still used as
     * the anchor, but each player receives a separate square-spiral slot so Bukkit's entity
     * collision resolution cannot launch a group that was teleported on top of itself.
     */
    public static Location getCollisionSafeTeleportLocation(@NotNull Location anchor, int index) {
        Location result = anchor.clone();
        if (index <= 0) return result;

        int remaining = index - 1;
        int ring = 1;
        while (remaining >= ring * 8) {
            remaining -= ring * 8;
            ring++;
        }
        int side = remaining / (ring * 2);
        int offset = remaining % (ring * 2);
        int x;
        int z;
        switch (side) {
            case 0 -> {
                x = -ring + offset;
                z = -ring;
            }
            case 1 -> {
                x = ring;
                z = -ring + offset;
            }
            case 2 -> {
                x = ring - offset;
                z = ring;
            }
            default -> {
                x = -ring;
                z = ring - offset;
            }
        }
        result.add(x * 1.25D, 0D, z * 1.25D);
        return result;
    }

    /**
     * Allocates stable personal spawns on the actual floor of a team's workspace. Prefer a
     * four-by-two-block rectangle, shifting it onto nearby clear floor when the anchor is beside a
     * counter or portal. Offline members reserve their positions too. Call once per round and
     * retain the returned assignments in the owning game state.
     */
    public static Map<UUID, Location> getTeamSpawnLocations(
            @NotNull Location anchor,
            @NotNull Collection<UUID> participants,
            @NotNull Predicate<Location> insideWorkspace) {
        List<UUID> members = participants.stream().distinct().sorted().toList();
        if (members.isEmpty()) return Map.of();
        Location center = centerOnBlock(anchor);
        List<Location> cells = new ArrayList<>();
        for (int z = -4; z <= 4; z++)
            for (int x = -4; x <= 4; x++) cells.add(center.clone().add(x, 0, z));
        cells.sort(Comparator.comparingDouble(center::distanceSquared));
        List<Location> safe =
                cells.stream()
                        .filter(insideWorkspace)
                        .filter(TeleportPositions::isSafeTeamSpawn)
                        .toList();
        int heading = Math.floorMod(Math.round(anchor.getYaw() / 90F), 4);
        int forwardX = heading == 1 ? -1 : heading == 3 ? 1 : 0;
        int forwardZ = heading == 0 ? 1 : heading == 2 ? -1 : 0;
        List<Location> positions = new ArrayList<>();
        for (Location candidate : cells) {
            List<Location> rectangle = new ArrayList<>();
            for (int forward : new int[] {1, -1})
                for (int lateral : new int[] {-2, 2})
                    rectangle.add(
                            candidate
                                    .clone()
                                    .add(
                                            forwardX * forward + forwardZ * lateral,
                                            0,
                                            forwardZ * forward - forwardX * lateral));
            if (safe.containsAll(rectangle)) {
                positions.addAll(rectangle);
                break;
            }
        }
        // Narrow/custom platforms and teams larger than four still use distinct supported cells.
        for (Location candidate : safe) {
            if (positions.size() >= members.size()) break;
            if (!positions.contains(candidate)) positions.add(candidate);
        }
        if (positions.size() < members.size())
            throw new IllegalStateException("Not enough safe team spawn positions near " + anchor);
        Map<UUID, Location> result = new LinkedHashMap<>();
        for (int index = 0; index < members.size(); index++)
            result.put(members.get(index), positions.get(index));
        return result;
    }

    private static boolean isSafeTeamSpawn(Location spot) {
        if (spot.getWorld() == null) return false;
        Block feet = spot.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block floor = feet.getRelative(0, -1, 0);
        if (!feet.isPassable()
                || !head.isPassable()
                || feet.isLiquid()
                || head.isLiquid()
                || !floor.isSolid()) return false;
        for (Block block : List.of(floor, feet, head))
            switch (block.getType()) {
                case NETHER_PORTAL,
                        END_PORTAL,
                        END_GATEWAY,
                        FIRE,
                        SOUL_FIRE,
                        CAMPFIRE,
                        SOUL_CAMPFIRE,
                        MAGMA_BLOCK,
                        CACTUS,
                        POWDER_SNOW -> {
                    return false;
                }
                default -> {}
            }
        var support = floor.getBoundingBox();
        return Math.abs(support.getMaxY() - spot.getY()) < 1e-6
                && support.getMinX() <= spot.getX() - 0.3D
                && support.getMaxX() >= spot.getX() + 0.3D
                && support.getMinZ() <= spot.getZ() - 0.3D
                && support.getMaxZ() >= spot.getZ() + 0.3D;
    }

    /**
     * Solid ground with passable feet and head space, so a scattered player neither falls nor
     * suffocates.
     */
    private static boolean isSafeLobbySpot(@NotNull Location spot) {
        World world = spot.getWorld();
        int x = spot.getBlockX();
        int y = spot.getBlockY();
        int z = spot.getBlockZ();
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && world.getBlockAt(x, y, z).isPassable()
                && world.getBlockAt(x, y + 1, z).isPassable();
    }

    /**
     * Aligns a player-captured point with the horizontal centre of the block they occupy. Height
     * and view direction deliberately remain untouched, because spawn surfaces may be slabs or
     * otherwise sit between whole Y coordinates.
     */
    public static Location centerOnBlock(@NotNull Location location) {
        Location centered = location.clone();
        centered.setX(location.getBlockX() + 0.5D);
        centered.setZ(location.getBlockZ() + 0.5D);
        return centered;
    }
}
