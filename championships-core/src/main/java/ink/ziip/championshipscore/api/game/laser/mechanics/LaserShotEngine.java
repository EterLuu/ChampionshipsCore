package ink.ziip.championshipscore.api.game.laser.mechanics;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared ray tracing and pellet aggregation; target eligibility and effects belong to the game. */
public final class LaserShotEngine {
    public interface Context {
        /** Rechecks the current match and the shooter's gameplay eligibility. */
        boolean current();

        /** Includes friendly body blocking and any game-specific interceptable entities. */
        boolean collidable(Entity entity);

        boolean enemy(Player player);

        void beam(World world, Location eye, Vector direction, double distance);

        default void sound(Player shooter, LaserGun gun) {
            LaserGunMechanics.playGunSound(shooter, gun);
        }

        default void pelletHit(Player player, LaserGun.HitRegion region) {}

        default void intercept(Entity entity, Vector point) {}

        /** Receives at most one aggregated hit per victim in this shot. */
        void damage(Player player, double damage, LaserGun.HitRegion region, Vector point);
    }

    private record Hit(double damage, LaserGun.HitRegion region, Vector point) {
        Hit add(double amount, LaserGun.HitRegion next, Vector nextPoint) {
            return new Hit(
                    damage + amount,
                    next == LaserGun.HitRegion.HEAD ? next : region,
                    next == LaserGun.HitRegion.HEAD ? nextPoint.clone() : point);
        }
    }

    private LaserShotEngine() {}

    /** Runs synchronously on the game's existing combat thread; never schedules delayed hits. */
    public static void fire(
            Player shooter, LaserGun gun, int burst, boolean aiming, Context context) {
        if (gun == null || !context.current()) return;
        Location eye = shooter.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return;
        Vector direction = eye.getDirection().normalize();
        double spread = LaserGunMechanics.combatSpread(shooter, gun, burst, aiming);
        Map<Player, Hit> hits = new LinkedHashMap<>();
        Set<UUID> intercepted = new HashSet<>();
        for (int pellet = 0; pellet < gun.pellets() && context.current(); pellet++) {
            Vector shot =
                    LaserGunMechanics.recoil(
                            LaserGunMechanics.spread(direction, spread), gun, burst);
            RayTraceResult block =
                    world.rayTraceBlocks(eye, shot, gun.range(), FluidCollisionMode.NEVER, true);
            double distance =
                    block == null ? gun.range() : block.getHitPosition().distance(eye.toVector());
            RayTraceResult hit =
                    world.rayTraceEntities(
                            eye,
                            shot,
                            distance,
                            0.15D,
                            entity ->
                                    !entity.getUniqueId().equals(shooter.getUniqueId())
                                            && context.collidable(entity));
            if (hit != null) distance = hit.getHitPosition().distance(eye.toVector());
            context.beam(world, eye, shot, distance);
            if (hit == null || !context.current()) continue;
            Entity target = hit.getHitEntity();
            if (target instanceof Player victim) {
                if (!context.enemy(victim)) continue;
                LaserGun.HitRegion region =
                        LaserGunMechanics.hitRegion(victim, hit.getHitPosition());
                context.pelletHit(victim, region);
                double amount = gun.damage(region, distance);
                hits.compute(
                        victim,
                        (ignored, previous) ->
                                previous == null
                                        ? new Hit(amount, region, hit.getHitPosition().clone())
                                        : previous.add(amount, region, hit.getHitPosition()));
            } else if (target != null && intercepted.add(target.getUniqueId())) {
                context.intercept(target, hit.getHitPosition());
            }
        }
        for (Map.Entry<Player, Hit> entry : hits.entrySet()) {
            if (!context.current()) break;
            if (context.enemy(entry.getKey()))
                context.damage(
                        entry.getKey(),
                        entry.getValue().damage(),
                        entry.getValue().region(),
                        entry.getValue().point());
        }
        context.sound(shooter, gun);
    }
}
