package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class RiptideDodgeEntitiesTest {
    @Test
    void serverPositionsMatchTheSimulatedPathsForEveryCourseAxis() {
        for (int[] axis : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            for (var mob : RiptideDodgeSchedule.Mob.values()) {
                for (int phase = 0; phase < 5; phase++) {
                    Arena arena = new Arena();
                    var geometry =
                            RiptideCourseGeometry.resolve(
                                    new Location(arena.world, .5, 80, .5),
                                    new Location(
                                            arena.world,
                                            .5 + axis[0] * 100,
                                            80,
                                            .5 + axis[1] * 100),
                                    7,
                                    9);
                    var run = new RiptideDodgeRun(mob.name(), 1234, 7, 9, phase);
                    var entities = new RiptideDodgeEntities(geometry, 30, IronHelmet::new);
                    var center = geometry.centerAt(30);
                    for (int tick = 0; tick < RiptideDodgeRun.DURATION_TICKS; tick++) {
                        var collisions = entities.tick(run);
                        int index = 0, active = 0;
                        for (var spawn : run.spawns()) {
                            if (spawn.tick() > tick) break;
                            FakeMob entity = arena.spawned.get(index++);
                            assertEquals(spawn.activeAt(tick), entity.valid);
                            if (!entity.valid) continue;
                            active++;
                            var position = spawn.positionAt(tick - spawn.tick() + 1);
                            assertEquals(
                                    center.getX() + axis[1] * position.x() + axis[0] * position.z(),
                                    entity.location.getX(),
                                    1e-9);
                            assertEquals(
                                    center.getZ() - axis[0] * position.x() + axis[1] * position.z(),
                                    entity.location.getZ(),
                                    1e-9);
                            assertEquals(
                                    center.getY() + position.y(), entity.location.getY(), 1e-9);
                        }
                        assertEquals(active, collisions.size());
                        assertTrue(
                                arena.peak
                                        <= RiptideDodgeSchedule.settingsFor(7, 9, phase, mob)
                                                .maximumActive());
                    }
                    assertTrue(
                            arena.spawned.stream()
                                    .allMatch(
                                            e ->
                                                    e.adult
                                                            && !e.removeWhenFarAway
                                                            && !e.canPickupItems));
                    for (FakeMob entity : arena.spawned) {
                        if (mob == RiptideDodgeSchedule.Mob.ZOMBIE
                                || mob == RiptideDodgeSchedule.Mob.HUSK
                                || mob == RiptideDodgeSchedule.Mob.SKELETON) {
                            assertNotNull(entity.helmet);
                            assertEquals(Material.IRON_HELMET, entity.helmet.getType());
                            assertEquals(0F, entity.helmetDropChance);
                        } else assertNull(entity.helmet);
                    }
                    entities.clear();
                    assertTrue(arena.spawned.stream().noneMatch(e -> e.valid));
                    entities.clear();
                }
            }
        }
    }

    @Test
    void completedRunClearsEveryEntityAndCannotSpawnAgain() {
        Arena arena = new Arena();
        var geometry =
                RiptideCourseGeometry.resolve(
                        new Location(arena.world, .5, 80, .5),
                        new Location(arena.world, .5, 80, 100.5),
                        7,
                        9);
        var run = new RiptideDodgeRun("CREEPER", 1234, 7, 9, 4);
        var entities = new RiptideDodgeEntities(geometry, 0, IronHelmet::new);
        while (!run.complete()) entities.tick(run);
        assertTrue(arena.spawned.stream().anyMatch(e -> e.valid));
        int count = arena.spawned.size();
        assertTrue(entities.tick(run).isEmpty());
        assertTrue(arena.spawned.stream().noneMatch(e -> e.valid));
        assertTrue(entities.tick(run).isEmpty());
        assertEquals(count, arena.spawned.size());
        assertEquals(RiptideDodgeRun.DURATION_TICKS, run.tickNumber());
    }

    @Test
    void deadEntitiesAreRemovedAndCollisionIncludesTheWholeMovementSegment() {
        Arena arena = new Arena();
        var geometry =
                RiptideCourseGeometry.resolve(
                        new Location(arena.world, .5, 80, .5),
                        new Location(arena.world, .5, 80, 100.5),
                        7,
                        9);
        var run = new RiptideDodgeRun("ZOMBIE", 1234, 7, 9);
        var entities = new RiptideDodgeEntities(geometry, 0, IronHelmet::new);
        while (arena.spawned.isEmpty()) entities.tick(run);
        FakeMob first = arena.spawned.getFirst();
        var before = RiptideDodgeRun.collisionBox(first.box());
        var collisions = entities.tick(run);
        assertEquals(1, collisions.size());
        assertEquals(.6D, collisions.getFirst().getWidthX(), 1e-9);
        assertEquals(
                .6D + run.spawns().getFirst().speed(), collisions.getFirst().getWidthZ(), 1e-9);
        assertTrue(collisions.getFirst().contains(before.getCenter()));
        first.dead = true;
        entities.tick(run);
        assertFalse(first.valid);
        entities.clear();
    }

    private static final class Arena {
        final List<FakeMob> spawned = new ArrayList<>();
        int peak;
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getName")) return "dodge-test";
                                    if (method.getName().equals("spawnEntity")) {
                                        FakeMob mob =
                                                new FakeMob(
                                                        (Location) args[0], (EntityType) args[1]);
                                        spawned.add(mob);
                                        peak =
                                                Math.max(
                                                        peak,
                                                        (int)
                                                                spawned.stream()
                                                                        .filter(e -> e.valid)
                                                                        .count());
                                        return mob.entity;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
    }

    /** Avoid Paper's server-backed ItemStack factory in the headless entity tests. */
    private static final class IronHelmet extends ItemStack {
        @Override
        public Material getType() {
            return Material.IRON_HELMET;
        }
    }

    private static final class FakeMob {
        Location location;
        final EntityType type;
        boolean valid = true, dead;
        boolean adult, removeWhenFarAway = true, canPickupItems = true;
        ItemStack helmet;
        float helmetDropChance = .085F;
        final EntityEquipment equipment =
                (EntityEquipment)
                        Proxy.newProxyInstance(
                                EntityEquipment.class.getClassLoader(),
                                new Class<?>[] {EntityEquipment.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "setHelmet" -> {
                                                helmet = (ItemStack) args[0];
                                                yield null;
                                            }
                                            case "setHelmetDropChance" -> {
                                                helmetDropChance = (float) args[0];
                                                yield null;
                                            }
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        final LivingEntity entity;

        FakeMob(Location location, EntityType type) {
            this.location = location.clone();
            this.type = type;
            adult = type != EntityType.ZOMBIE && type != EntityType.HUSK;
            Class<?> mobInterface =
                    switch (type) {
                        case CREEPER -> Creeper.class;
                        case ZOMBIE, HUSK -> Zombie.class;
                        case SKELETON -> Skeleton.class;
                        default -> LivingEntity.class;
                    };
            entity =
                    (LivingEntity)
                            Proxy.newProxyInstance(
                                    LivingEntity.class.getClassLoader(),
                                    new Class<?>[] {mobInterface},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getBoundingBox" -> box();
                                                case "isValid" -> valid;
                                                case "isDead" -> dead;
                                                case "remove" -> {
                                                    valid = false;
                                                    yield null;
                                                }
                                                case "setAdult" -> {
                                                    adult = true;
                                                    yield null;
                                                }
                                                case "getEquipment" -> equipment;
                                                case "setRemoveWhenFarAway" -> {
                                                    removeWhenFarAway = (boolean) args[0];
                                                    yield null;
                                                }
                                                case "setCanPickupItems" -> {
                                                    canPickupItems = (boolean) args[0];
                                                    yield null;
                                                }
                                                case "teleport" -> {
                                                    this.location = ((Location) args[0]).clone();
                                                    yield true;
                                                }
                                                case "setRotation",
                                                        "setVelocity",
                                                        "setAI",
                                                        "setGravity",
                                                        "setInvulnerable",
                                                        "setSilent",
                                                        "setCollidable",
                                                        "setPersistent",
                                                        "setExplosionRadius" ->
                                                        null;
                                                default ->
                                                        throw new UnsupportedOperationException(
                                                                method.getName());
                                            });
        }

        BoundingBox box() {
            double half = type == EntityType.SPIDER ? .7 : .3;
            double height =
                    type == EntityType.SPIDER ? .9 : type == EntityType.CREEPER ? 1.7 : 1.95;
            if (!adult) height *= .5;
            return new BoundingBox(
                    location.getX() - half,
                    location.getY(),
                    location.getZ() - half,
                    location.getX() + half,
                    location.getY() + height,
                    location.getZ() + half);
        }
    }
}
