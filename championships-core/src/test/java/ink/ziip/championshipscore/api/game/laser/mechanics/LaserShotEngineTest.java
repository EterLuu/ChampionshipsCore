package ink.ziip.championshipscore.api.game.laser.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

class LaserShotEngineTest {
    @Test
    void shotgunCountsEveryPelletAndAppliesOneAggregatedHeadshot() {
        var fixture = new Fixture();
        fixture.target = fixture.victim;
        fixture.headshot = true;
        LaserShotEngine.fire(fixture.shooter, LaserGun.SHOTGUN, 0, false, fixture);
        assertEquals(8, fixture.pellets);
        assertEquals(8, fixture.beams);
        assertEquals(1, fixture.hits.size());
        assertEquals(
                LaserGun.SHOTGUN.damage(LaserGun.HitRegion.HEAD, 4) * 8,
                fixture.hits.getFirst().damage(),
                1e-9);
        assertEquals(LaserGun.HitRegion.HEAD, fixture.hits.getFirst().region());
    }

    @Test
    void friendlyPlayersBlockTheRayWithoutDamageOrEnemyHitStatistics() {
        var fixture = new Fixture();
        fixture.target = fixture.friend;
        LaserShotEngine.fire(fixture.shooter, LaserGun.RIFLE, 0, false, fixture);
        assertEquals(1, fixture.beams);
        assertEquals(0, fixture.pellets);
        assertTrue(fixture.hits.isEmpty());
    }

    @Test
    void gameEligibilityExcludesSpectatorsAndTheShooter() {
        var fixture = new Fixture();
        for (Entity ignored : List.of(fixture.spectator, fixture.shooter)) {
            fixture.target = ignored;
            LaserShotEngine.fire(fixture.shooter, LaserGun.RIFLE, 0, false, fixture);
        }
        assertEquals(2, fixture.beams);
        assertEquals(0, fixture.pellets);
        assertTrue(fixture.hits.isEmpty());
    }

    @Test
    void solidBlockLimitsEntityRayAndParticleLength() {
        var fixture = new Fixture();
        fixture.wallDistance = 2;
        LaserShotEngine.fire(fixture.shooter, LaserGun.RIFLE, 0, false, fixture);
        assertEquals(2, fixture.entityRange);
        assertEquals(2, fixture.beamDistance);
        assertTrue(fixture.hits.isEmpty());
    }

    @Test
    void gameSpecificEntitiesUseInterceptionWithoutWeaponDamage() {
        var fixture = new Fixture();
        fixture.target = fixture.grenade;
        LaserShotEngine.fire(fixture.shooter, LaserGun.SHOTGUN, 0, false, fixture);
        assertEquals(1, fixture.interceptions);
        assertEquals(0, fixture.pellets);
        assertTrue(fixture.hits.isEmpty());
    }

    @Test
    void settledOrInvalidatedMatchStopsPendingPelletsAndDamage() {
        var fixture = new Fixture();
        fixture.target = fixture.victim;
        fixture.invalidateOnPellet = true;
        LaserShotEngine.fire(fixture.shooter, LaserGun.SHOTGUN, 0, false, fixture);
        assertEquals(1, fixture.pellets);
        assertTrue(fixture.hits.isEmpty());
        int beams = fixture.beams;
        LaserShotEngine.fire(fixture.shooter, LaserGun.RIFLE, 0, false, fixture);
        assertEquals(beams, fixture.beams);
    }

    private record Damage(double damage, LaserGun.HitRegion region) {}

    private static final class Fixture implements LaserShotEngine.Context {
        final World world;
        final Player shooter;
        final Player victim;
        final Player friend;
        final Player spectator;
        final Entity grenade;
        final List<Damage> hits = new ArrayList<>();
        Entity target;
        boolean live = true;
        boolean headshot;
        boolean invalidateOnPellet;
        int pellets;
        int beams;
        int interceptions;
        double wallDistance;
        double entityRange;
        double beamDistance;

        Fixture() {
            world =
                    (World)
                            Proxy.newProxyInstance(
                                    World.class.getClassLoader(),
                                    new Class<?>[] {World.class},
                                    (proxy, method, args) -> {
                                        switch (method.getName()) {
                                            case "rayTraceBlocks":
                                                return wallDistance > 0
                                                        ? new RayTraceResult(
                                                                new Vector(0, 65, wallDistance))
                                                        : null;
                                            case "rayTraceEntities":
                                                entityRange = (double) args[2];
                                                @SuppressWarnings("unchecked")
                                                Predicate<Entity> allowed =
                                                        (Predicate<Entity>) args[4];
                                                return target != null && allowed.test(target)
                                                        ? new RayTraceResult(
                                                                new Vector(0, 65, 4), target)
                                                        : null;
                                            case "equals":
                                                return proxy == args[0];
                                            case "hashCode":
                                                return System.identityHashCode(proxy);
                                            default:
                                                return null;
                                        }
                                    });
            shooter = entity(Player.class);
            victim = entity(Player.class);
            friend = entity(Player.class);
            spectator = entity(Player.class);
            grenade = entity(Entity.class);
        }

        private <T extends Entity> T entity(Class<T> type) {
            UUID uuid = UUID.randomUUID();
            return type.cast(
                    Proxy.newProxyInstance(
                            type.getClassLoader(),
                            new Class<?>[] {type},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> uuid;
                                        case "getWorld" -> world;
                                        case "getEyeLocation", "getLocation" ->
                                                new Location(world, 0, 65, 0);
                                        case "getVelocity" -> new Vector();
                                        case "isOnGround" -> true;
                                        case "isSneaking" -> false;
                                        case "getBoundingBox" ->
                                                new BoundingBox(
                                                        0,
                                                        headshot ? 63.5 : 64,
                                                        3.5,
                                                        0.6,
                                                        headshot ? 65.3 : 65.8,
                                                        4.5);
                                        case "equals" -> proxy == args[0];
                                        case "hashCode" -> System.identityHashCode(proxy);
                                        default -> null;
                                    }));
        }

        @Override
        public boolean current() {
            return live;
        }

        @Override
        public boolean collidable(Entity entity) {
            return entity != spectator;
        }

        @Override
        public boolean enemy(Player player) {
            return player == victim;
        }

        @Override
        public void sound(Player shooter, LaserGun gun) {}

        @Override
        public void beam(World world, Location eye, Vector direction, double distance) {
            beams++;
            beamDistance = distance;
        }

        @Override
        public void pelletHit(Player player, LaserGun.HitRegion region) {
            pellets++;
            if (invalidateOnPellet) live = false;
        }

        @Override
        public void intercept(Entity entity, Vector point) {
            interceptions++;
        }

        @Override
        public void damage(Player player, double damage, LaserGun.HitRegion region, Vector point) {
            hits.add(new Damage(damage, region));
            assertEquals(new Vector(0, 65, 4), point);
        }
    }
}
