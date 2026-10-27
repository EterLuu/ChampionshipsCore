package ink.ziip.championshipscore.api.game.tntrun.runtime;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Owns footstep detection and fixed-delay terrain removal on Paper's tick thread. */
final class TNTRunFloorTracker implements AutoCloseable {
    private static final int DELAY_TICKS = 8;
    private final Plugin plugin;
    private final Supplier<List<Player>> participants;
    private final Predicate<Player> eligible;
    private final BooleanSupplier current;
    private final Map<Cell, Removal> pending = new HashMap<>();
    private final List<ArrayDeque<Removal>> due = new ArrayList<>(DELAY_TICKS + 1);
    private BukkitTask sampler;
    private boolean closed;
    private long tick;

    TNTRunFloorTracker(
            Plugin plugin,
            Supplier<List<Player>> participants,
            Predicate<Player> eligible,
            BooleanSupplier current) {
        this.plugin = plugin;
        this.participants = participants;
        this.eligible = eligible;
        this.current = current;
        for (int i = 0; i <= DELAY_TICKS; i++) due.add(new ArrayDeque<>());
    }

    void start() {
        if (sampler != null || closed) return;
        sampler = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    private void tick() {
        if (closed || !plugin.isEnabled() || !current.getAsBoolean()) {
            close();
            return;
        }
        tick++;
        removeDue();
        for (Player player : participants.get()) {
            if (valid(player)) observe(player);
        }
    }

    private void observe(Player player) {
        Location location = player.getLocation();
        TNTRunBlockProbe.Hit hit = TNTRunBlockProbe.find(location, .3);
        if (hit == null) return;
        World world = location.getWorld();
        Block block = hit.block();
        Cell cell = new Cell(world.getUID(), block.getX(), block.getY(), block.getZ());
        Material support =
                cell.y > world.getMinHeight()
                        ? world.getBlockAt(cell.x, cell.y - 1, cell.z).getType()
                        : Material.AIR;
        long deadline = tick + DELAY_TICKS;
        Removal removal = pending.get(cell);
        if (removal == null || removal.material != hit.material() || removal.support != support) {
            removal = new Removal(cell, hit.material(), support, deadline);
            pending.put(cell, removal);
            schedule(removal);
        }
        UUID id = player.getUniqueId();
        Owner old = removal.owners.get(id);
        if (old == null || old.player != player) {
            removal.owners.put(
                    id,
                    new Owner(player, location.getX(), location.getY(), location.getZ(), deadline));
        }
    }

    private void schedule(Removal removal) {
        due.get((int) (removal.tick % due.size())).addLast(removal);
    }

    private void removeDue() {
        ArrayDeque<Removal> bucket = due.get((int) (tick % due.size()));
        for (Removal removal; (removal = bucket.pollFirst()) != null; ) {
            if (pending.get(removal.cell) != removal) continue;
            Owner owner = null;
            long next = Long.MAX_VALUE;
            Iterator<Owner> owners = removal.owners.values().iterator();
            while (owners.hasNext()) {
                Owner candidate = owners.next();
                if (!valid(candidate.player)
                        || !candidate.player.getWorld().getUID().equals(removal.cell.world)) {
                    owners.remove();
                    continue;
                }
                if (candidate.deadline <= tick) owner = candidate;
                else next = Math.min(next, candidate.deadline);
            }
            // Each contributor keeps its own delay if the player who stepped here first leaves.
            if (owner == null && next != Long.MAX_VALUE) {
                removal.tick = next;
                schedule(removal);
                continue;
            }
            pending.remove(removal.cell, removal);
            if (owner == null) continue;
            World world = plugin.getServer().getWorld(removal.cell.world);
            if (world == null || !world.isChunkLoaded(removal.cell.x >> 4, removal.cell.z >> 4))
                continue;
            Block block = world.getBlockAt(removal.cell.x, removal.cell.y, removal.cell.z);
            if (block.getType() != removal.material) continue;
            world.playSound(
                    new Location(world, owner.x, owner.y, owner.z),
                    "minecraft:block.sand.break",
                    3,
                    1);
            block.setType(Material.AIR);
            if (removal.cell.y > world.getMinHeight()) {
                Block support =
                        world.getBlockAt(removal.cell.x, removal.cell.y - 1, removal.cell.z);
                if (support.getType() == removal.support) support.setType(Material.AIR);
            }
        }
    }

    private boolean valid(Player player) {
        return player != null
                && player.isOnline()
                && plugin.getServer().getPlayer(player.getUniqueId()) == player
                && eligible.test(player);
    }

    /** Called by the owning arena before restoring terrain or changing runs. */
    @Override
    public void close() {
        closed = true;
        if (sampler != null) sampler.cancel();
        pending.clear();
        due.forEach(ArrayDeque::clear);
    }

    private record Cell(UUID world, int x, int y, int z) {}

    private record Owner(Player player, double x, double y, double z, long deadline) {}

    private static final class Removal {
        final Cell cell;
        final Material material, support;
        final Map<UUID, Owner> owners = new LinkedHashMap<>();
        long tick;

        Removal(Cell cell, Material material, Material support, long tick) {
            this.cell = cell;
            this.material = material;
            this.support = support;
            this.tick = tick;
        }
    }
}
