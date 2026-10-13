package ink.ziip.championshipscore.api.game.laser.mechanics;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.KineticWeapon;
import io.papermc.paper.datacomponent.item.UseEffects;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Shared weapon behaviour for LaserBox and LaserShowdown; match rules remain game-owned. */
public final class LaserGunMechanics {
    public static final int AIM_INPUT_GRACE_TICKS = 7;
    private static final double SNIPER_HIP_FIRE_SPREAD_DEGREES = 6.0D;

    private LaserGunMechanics() {}

    public static boolean automatic(LaserGun gun) {
        return gun == LaserGun.SMG || gun == LaserGun.MACHINE;
    }

    /** Retains normal tick rounding without accumulating catch-up shots after late input. */
    public static double nextShot(int now, Double deadline, LaserGun gun) {
        double base =
                deadline != null && now - deadline < Math.max(1.0D, gun.fireIntervalTicks() / 2.0D)
                        ? deadline
                        : now;
        return base + gun.fireIntervalTicks();
    }

    public static int slownessAmplifier(LaserGun gun, boolean aiming) {
        if (gun == LaserGun.SNIPER) return aiming ? 2 : 0;
        return gun == LaserGun.MACHINE ? 0 : -1;
    }

    public static int burstShot(int now, Integer previous, int previousBurst) {
        return previous != null && now - previous < 32 ? previousBurst + 1 : 0;
    }

    public static int speedAmplifier(LaserGun gun, boolean emptyHands) {
        if (emptyHands || gun == LaserGun.SMG) return 1;
        return gun == LaserGun.RIFLE || gun == LaserGun.MACHINE || gun == LaserGun.SNIPER ? -1 : 0;
    }

    public static double combatSpread(Player player, LaserGun gun, int burstShot, boolean aiming) {
        Vector velocity = player.getVelocity();
        boolean moving =
                velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 0.0025D;
        return combatSpread(
                gun, burstShot, player.isOnGround(), player.isSneaking(), moving, aiming);
    }

    public static double combatSpread(
            LaserGun gun,
            int burstShot,
            boolean grounded,
            boolean sneaking,
            boolean moving,
            boolean aiming) {
        double spread = gun.spreadDegrees(burstShot);
        double maximum = gun.spreadDegrees();
        if (!grounded) spread += maximum * (gun == LaserGun.SMG ? 2.25D : 4.0D);
        else if (sneaking) spread = gun.crouchedSpread(spread, moving);
        else if (moving) spread += maximum * (gun == LaserGun.SMG ? 1.0D : 1.6D);
        if (aiming && gun == LaserGun.SNIPER) spread *= !grounded ? 0.85D : moving ? 0.65D : 0.25D;
        // Unscoped shots remain inaccurate even on the first shot and while crouched.
        if (!aiming && gun == LaserGun.SNIPER) spread += SNIPER_HIP_FIRE_SPREAD_DEGREES;
        return spread;
    }

    public static void configureGunItem(ItemStack result, NamespacedKey fireRateKey) {
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.addAttributeModifier(
                    Attribute.ATTACK_SPEED,
                    new AttributeModifier(
                            fireRateKey,
                            100.0D,
                            AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.MAINHAND));
            result.setItemMeta(meta);
        }
        if (automatic(LaserGun.fromMaterial(result.getType()))) {
            result.setData(
                    DataComponentTypes.KINETIC_WEAPON,
                    KineticWeapon.kineticWeapon()
                            .delayTicks(Integer.MAX_VALUE)
                            .forwardMovement(0.0F)
                            .damageMultiplier(0.0F));
            result.setData(
                    DataComponentTypes.USE_EFFECTS,
                    UseEffects.useEffects().canSprint(true).speedMultiplier(1.0F));
        }
    }

    public static void playGunSound(Player shooter, LaserGun gun) {
        Sound sound;
        float volume;
        float pitch;
        switch (gun) {
            case SMG -> {
                sound = Sound.ENTITY_ARROW_SHOOT;
                volume = 0.75F;
                pitch = 1.65F;
            }
            case RIFLE -> {
                sound = Sound.ENTITY_FIREWORK_ROCKET_BLAST;
                volume = 1.08F;
                pitch = 0.82F;
            }
            case MACHINE -> {
                sound = Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR;
                volume = 1.15F;
                pitch = 0.65F;
            }
            case SNIPER -> {
                sound = Sound.ENTITY_GENERIC_EXPLODE;
                volume = 1.25F;
                pitch = 0.5F;
            }
            case SHOTGUN -> {
                sound = Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
                volume = 1.3F;
                pitch = 0.42F;
            }
            default -> throw new IllegalStateException("Unknown laser gun: " + gun);
        }
        World world = shooter.getWorld();
        if (world != null) world.playSound(shooter.getLocation(), sound, volume, pitch);
    }

    public static Vector spread(Vector direction, double degrees) {
        if (degrees <= 0.0D) return direction.clone();
        double radians = Math.toRadians(degrees);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return perturb(
                direction,
                Math.tan(random.nextDouble(-radians, radians)),
                Math.tan(random.nextDouble(-radians, radians)));
    }

    public static Vector recoil(Vector direction, LaserGun gun, int burstShot) {
        double yaw = gun.recoilYawDegrees(burstShot);
        double pitch = gun.recoilPitchDegrees(burstShot);
        if (yaw == 0.0D && pitch == 0.0D) return direction;
        return perturb(direction, Math.tan(Math.toRadians(yaw)), Math.tan(Math.toRadians(pitch)));
    }

    public static Vector perturb(Vector direction, double horizontalOffset, double verticalOffset) {
        Vector forward = direction.clone().normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(up);
        if (right.lengthSquared() < 0.0001D) right = new Vector(1, 0, 0);
        right.normalize();
        Vector vertical = right.clone().crossProduct(forward).normalize();
        return forward.add(right.multiply(horizontalOffset))
                .add(vertical.multiply(verticalOffset))
                .normalize();
    }

    public static LaserGun.HitRegion hitRegion(Player victim, Vector point) {
        double min = victim.getBoundingBox().getMinY();
        double max = victim.getBoundingBox().getMaxY();
        double height = Math.max(0.1D, max - min);
        double relative = (point.getY() - min) / height;
        if (relative >= 0.72D) return LaserGun.HitRegion.HEAD;
        if (relative <= 0.35D) return LaserGun.HitRegion.LEGS;
        return LaserGun.HitRegion.BODY;
    }
}
