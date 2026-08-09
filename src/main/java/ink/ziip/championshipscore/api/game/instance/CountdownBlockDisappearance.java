package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Removes a configured map cuboid over the first three seconds of the final countdown. */
public final class CountdownBlockDisappearance {
    public static final int MAX_SELECTION_VOLUME = 250_000;
    private static final int DURATION_TICKS = 60;

    private final ChampionshipsCore plugin;
    private final BaseGameInstance instance;
    private final List<BlockState> snapshots = new ArrayList<>();
    private BukkitTask task;

    CountdownBlockDisappearance(@NotNull ChampionshipsCore plugin, @NotNull BaseGameInstance instance) {
        this.plugin = plugin;
        this.instance = instance;
    }

    void start(int countdownSeconds) {
        restore();
        BaseGameConfig config = instance.getGameConfig();
        Vector[] bounds = instance.getCountdownBlockDisappearanceBounds();
        if (bounds == null || bounds[0] == null || bounds[1] == null) return;

        World world = instance.getSpectatorSpawnLocation() == null
                ? null : instance.getSpectatorSpawnLocation().getWorld();
        if (world == null) return;

        int minX = Math.min(bounds[0].getBlockX(), bounds[1].getBlockX());
        int maxX = Math.max(bounds[0].getBlockX(), bounds[1].getBlockX());
        int minY = Math.min(bounds[0].getBlockY(), bounds[1].getBlockY());
        int maxY = Math.max(bounds[0].getBlockY(), bounds[1].getBlockY());
        int minZ = Math.min(bounds[0].getBlockZ(), bounds[1].getBlockZ());
        int maxZ = Math.max(bounds[0].getBlockZ(), bounds[1].getBlockZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume <= 0 || volume > MAX_SELECTION_VOLUME) {
            instance.logGame(java.util.logging.Level.WARNING, "倒计时方块消失",
                    "选区体积超出限制，已跳过 | 体积=" + volume + " | 上限=" + MAX_SELECTION_VOLUME);
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = world.getBlockAt(x, y, z).getState();
                    if (state.getType() != Material.AIR && !state.getType().isAir()) snapshots.add(state);
                }
            }
        }
        if (snapshots.isEmpty()) return;

        Mode mode = Mode.parse(config.getCountdownBlockDisappearanceMode());
        if (mode == Mode.DIRECT) {
            removeRange(0, snapshots.size());
            return;
        }

        if (mode == Mode.RANDOM) {
            snapshots.sort(Comparator.comparingInt(BlockState::getX)
                    .thenComparingInt(BlockState::getY).thenComparingInt(BlockState::getZ));
            java.util.Collections.shuffle(snapshots, ThreadLocalRandom.current());
        } else {
            int axisMin = switch (mode) {
                case DOOR_EAST_WEST -> minX;
                case DOOR_NORTH_SOUTH -> minZ;
                default -> minY;
            };
            int axisMax = switch (mode) {
                case DOOR_EAST_WEST -> maxX;
                case DOOR_NORTH_SOUTH -> maxZ;
                default -> maxY;
            };
            snapshots.sort(Comparator.comparingInt((BlockState state) -> doorDistance(
                            axisValue(mode, state), axisMin, axisMax))
                    .thenComparingInt(BlockState::getX)
                    .thenComparingInt(BlockState::getY)
                    .thenComparingInt(BlockState::getZ));
        }

        int ticks = Math.max(1, Math.min(DURATION_TICKS, Math.max(1, countdownSeconds) * 20));
        final int[] elapsed = {0};
        final int[] removed = {0};
        final int duration = ticks;
        final boolean doorMode = mode == Mode.DOOR_EAST_WEST || mode == Mode.DOOR_NORTH_SOUTH
                || mode == Mode.DOOR_VERTICAL;
        final int doorAxisMin = switch (mode) {
            case DOOR_EAST_WEST -> minX;
            case DOOR_NORTH_SOUTH -> minZ;
            default -> minY;
        };
        final int doorAxisMax = switch (mode) {
            case DOOR_EAST_WEST -> maxX;
            case DOOR_NORTH_SOUTH -> maxZ;
            default -> maxY;
        };
        final int doorLayerCount = doorDistance(doorAxisMax, doorAxisMin, doorAxisMax) + 1;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (elapsed[0] >= duration) {
                cancelTask();
                return;
            }
            int target;
            if (doorMode) {
                int targetDistance = (int) Math.ceil((double) doorLayerCount
                        * (elapsed[0] + 1) / duration) - 1;
                target = removed[0];
                while (target < snapshots.size()) {
                    BlockState state = snapshots.get(target);
                    int axis = axisValue(mode, state);
                    if (doorDistance(axis, doorAxisMin, doorAxisMax) > targetDistance) break;
                    target++;
                }
            } else {
                target = (int) Math.ceil((double) snapshots.size() * (elapsed[0] + 1) / duration);
            }
            removeRange(removed[0], target);
            removed[0] = target;
            elapsed[0]++;
            if (target >= snapshots.size()) cancelTask();
        }, 0L, 1L);
    }

    /** Restore the captured map state before the next run or before an instance is unloaded. */
    void restore() {
        cancelTask();
        for (BlockState state : snapshots) {
            World world = state.getWorld();
            if (world != null && Bukkit.getWorld(world.getUID()) != null) state.update(true, false);
        }
        snapshots.clear();
    }

    void cancel() {
        cancelTask();
    }

    private void removeRange(int fromInclusive, int toExclusive) {
        int from = Math.max(0, Math.min(fromInclusive, snapshots.size()));
        int limit = Math.max(from, Math.min(toExclusive, snapshots.size()));
        for (int index = from; index < limit; index++) {
            BlockState state = snapshots.get(index);
            if (state.getWorld() != null) state.getWorld().getBlockAt(state.getX(), state.getY(), state.getZ())
                    .setType(Material.AIR, false);
        }
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static int doorDistance(int axis, int min, int max) {
        int size = max - min + 1;
        int centerWidth = size % 2 == 0 ? 2 : 3;
        int centerMin = min + (size - centerWidth) / 2;
        int centerMax = centerMin + centerWidth - 1;
        if (axis < centerMin) return centerMin - axis;
        if (axis > centerMax) return axis - centerMax;
        return 0;
    }

    private static int axisValue(Mode mode, BlockState state) {
        return switch (mode) {
            case DOOR_EAST_WEST -> state.getX();
            case DOOR_NORTH_SOUTH -> state.getZ();
            default -> state.getY();
        };
    }

    private enum Mode {
        RANDOM, DOOR_EAST_WEST, DOOR_NORTH_SOUTH, DOOR_VERTICAL, DIRECT;

        static Mode parse(@Nullable String value) {
            if (value == null) return RANDOM;
            if ("DOOR_HORIZONTAL".equalsIgnoreCase(value)) return DOOR_EAST_WEST;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return RANDOM;
            }
        }
    }

}
