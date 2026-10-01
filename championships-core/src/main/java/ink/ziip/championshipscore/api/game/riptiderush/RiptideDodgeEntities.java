package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.api.game.riptiderush.RiptideDodgeSchedule.Direction;
import ink.ziip.championshipscore.api.game.riptiderush.RiptideDodgeSchedule.Position;
import ink.ziip.championshipscore.api.game.riptiderush.RiptideDodgeSchedule.Spawn;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** One movement, collision and cleanup implementation for scored rounds and editor trials. */
final class RiptideDodgeEntities {
    private record Hazard(LivingEntity entity, Spawn spawn) { }
    private final List<Hazard> hazards = new ArrayList<>();
    private final RiptideCourseGeometry geometry;
    private final Location center;
    private final Supplier<ItemStack> helmetFactory;

    RiptideDodgeEntities(RiptideCourseGeometry geometry, int stoppedStep) {
        this(geometry, stoppedStep, () -> new ItemStack(Material.IRON_HELMET));
    }

    RiptideDodgeEntities(RiptideCourseGeometry geometry, int stoppedStep, Supplier<ItemStack> helmetFactory) {
        this.geometry = geometry;
        center = geometry.centerAt(stoppedStep);
        this.helmetFactory = helmetFactory;
    }

    List<BoundingBox> tick(RiptideDodgeRun run) {
        if (run.complete()) { clear(); return List.of(); }
        int tick = run.tickNumber();
        // Release retired slots before creating another entity on the same tick.
        hazards.removeIf(hazard -> {
            LivingEntity entity = hazard.entity();
            if (!entity.isValid() || entity.isDead() || tick - hazard.spawn().tick() >= hazard.spawn().lifetimeTicks()) {
                if (entity.isValid()) entity.remove();
                return true;
            }
            return false;
        });
        for (Spawn spawn : run.tick()) spawn(spawn);
        List<BoundingBox> collisions = new ArrayList<>(hazards.size());
        for (Hazard hazard : hazards) {
            LivingEntity entity = hazard.entity();
            BoundingBox before = RiptideDodgeRun.collisionBox(entity.getBoundingBox());
            Location next = location(hazard.spawn(), tick - hazard.spawn().tick() + 1);
            entity.teleport(next);
            entity.setRotation(next.getYaw(), next.getPitch());
            entity.setVelocity(new Vector());
            // Sweep the entire cardinal movement, including falling mobs, between server ticks.
            collisions.add(before.union(RiptideDodgeRun.collisionBox(entity.getBoundingBox())));
        }
        return List.copyOf(collisions);
    }

    private void spawn(Spawn spawn) {
        if (center.getWorld() == null) return;
        EntityType type = switch (spawn.mob()) {
            case ZOMBIE -> EntityType.ZOMBIE;
            case HUSK -> EntityType.HUSK;
            case SKELETON -> EntityType.SKELETON;
            case SPIDER -> EntityType.SPIDER;
            case CREEPER -> EntityType.CREEPER;
        };
        Entity raw = center.getWorld().spawnEntity(location(spawn, 0), type);
        if (!(raw instanceof LivingEntity entity)) { raw.remove(); return; }
        entity.setAI(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setCollidable(false);
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        // Random baby zombies/husks would change the calibrated native collision height.
        if (entity instanceof Zombie zombie) zombie.setAdult();
        if (entity instanceof Zombie || entity instanceof AbstractSkeleton) {
            EntityEquipment equipment = entity.getEquipment();
            equipment.setHelmet(helmetFactory.get());
            equipment.setHelmetDropChance(0F);
        }
        if (entity instanceof Creeper creeper) creeper.setExplosionRadius(0);
        hazards.add(new Hazard(entity, spawn));
    }

    private Location location(Spawn spawn, int age) {
        Position position = spawn.positionAt(age);
        Location result = center.clone().add(toWorld(position.x(), position.y(), position.z()));
        Direction direction = spawn.direction();
        result.setDirection(toWorld(direction.x, direction.y, direction.z));
        return result;
    }

    private Vector toWorld(double lateral, double height, double forward) {
        return new Vector(geometry.stepZ() * lateral + geometry.stepX() * forward, height,
                -geometry.stepX() * lateral + geometry.stepZ() * forward);
    }

    void clear() {
        for (Hazard hazard : hazards) if (hazard.entity().isValid()) hazard.entity().remove();
        hazards.clear();
    }
}
