package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import java.util.*;

/** Fixed course obstacles animated alongside raft movement, with ordinary PASS fall rules. */
final class RiptideRhythmRun implements AutoCloseable {
    private final RiptideCourseGeometry geometry;
    private final List<RiptideCoursePlan.Level> levels;
    private final Map<RiptideCoursePlan.Level, Gate> active = new LinkedHashMap<>();
    private int next;

    private static final class Gate {
        final List<BlockState> originals = new ArrayList<>();
        final double speed;
        int tick;
        BitSet mask;
        Gate(double speed) { this.speed = speed; }
    }

    RiptideRhythmRun(RiptideCourseGeometry geometry, List<RiptideCoursePlan.Level> levels) {
        this.geometry = geometry;
        this.levels = levels.stream().filter(l -> l.type() == RiptideLevelType.RHYTHM).toList();
    }

    void tick(int raftStep, double speed, Collection<Player> players) {
        int lead = geometry.halfLength() + Math.min(RiptideCourseReveal.LEAD_BLOCKS, (int) Math.ceil(speed * 2));
        while (next < levels.size() && raftStep + lead >= levels.get(next).step()) {
            var level = levels.get(next++);
            if (raftStep > level.step() + geometry.halfLength() + 1) continue;
            var gate = new Gate(speed);
            for (int lateral = -geometry.halfWidth(); lateral <= geometry.halfWidth(); lateral++)
                for (int y = 1; y <= 4; y++) gate.originals.add(geometry.centerAt(level.step()).getWorld()
                        .getBlockAt(geometry.blockX(level.step(), lateral), geometry.floorY() + y,
                                geometry.blockZ(level.step(), lateral)).getState());
            active.put(level, gate);
        }
        var iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var level = entry.getKey(); var gate = entry.getValue();
            if (raftStep > level.step() + geometry.halfLength() + 1) {
                gate.originals.forEach(block -> block.update(true, false)); iterator.remove(); continue;
            }
            render(level, gate, players);
            gate.tick++;
        }
    }

    private void render(RiptideCoursePlan.Level level, Gate gate, Collection<Player> players) {
        var world = geometry.centerAt(level.step()).getWorld();
        var mask = new BitSet();
        var closed = new ArrayList<BoundingBox>();
        for (int lateral = -geometry.halfWidth(); lateral <= geometry.halfWidth(); lateral++) {
            boolean[] openings = new boolean[4];
            for (int y = 1; y <= 3; y++) {
                openings[y] = RiptideRhythmGate.opening(level.variant(), level.mirrored(), gate.tick, gate.speed, lateral, y);
                if (openings[y]) mask.set((lateral + geometry.halfWidth()) * 3 + y - 1);
            }
            boolean passable = openings[2] && (openings[1] || openings[3]);
            for (int y = 1; y <= 4; y++) {
                var block = world.getBlockAt(geometry.blockX(level.step(), lateral), geometry.floorY() + y,
                        geometry.blockZ(level.step(), lateral));
                // Every rhythm gate uses the same iron body and the same red/green
                // status cap; only the opening mask changes between variants.
                Material material = y == 4 ? passable ? Material.LIME_CONCRETE : Material.RED_CONCRETE
                        : openings[y] ? Material.AIR : Material.IRON_BLOCK;
                if (block.getType() != material) block.setType(material, false);
                if (material != Material.AIR) closed.add(new BoundingBox(block.getX(), block.getY(), block.getZ(),
                        block.getX() + 1, block.getY() + 1, block.getZ() + 1));
            }
        }
        // Resolve the whole wall at once so a player spanning columns is displaced only once.
        for (var player : players) {
            var box = player.getBoundingBox();
            if (closed.stream().noneMatch(box::overlaps)) continue;
            var location = player.getLocation();
            double offset = geometry.forwardOffset(location, level.step());
            double radius = (geometry.stepX() == 0 ? box.getWidthZ() : box.getWidthX()) / 2;
            double direction = offset > 0 ? 1 : -1;
            double displacement = direction * (.5 + radius + .01) - offset;
            player.teleport(location.clone().add(geometry.stepX() * displacement, 0, geometry.stepZ() * displacement));
        }
        if (!mask.equals(gate.mask)) {
            String sound = mask.isEmpty() ? "minecraft:block.note_block.hat" : "minecraft:block.note_block.bell";
            for (var player : players) player.playSound(geometry.centerAt(level.step()), sound, 1F, mask.isEmpty() ? 1F : 1.5F);
            gate.mask = mask;
        }
    }

    @Override public void close() {
        active.values().forEach(gate -> gate.originals.forEach(block -> block.update(true, false)));
        active.clear();
    }
}
