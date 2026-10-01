package ink.ziip.championshipscore.api.game.buildmart;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ground contact, one-tick confirmation and a single mostly vertical impulse, like Ace Race pads. */
final class BuildMartJumpPads {
    static final double TARGET_Y = 180;
    static final double FORWARD_SPEED = .3;
    static final double FLIGHT_MARGIN = 4;
    private static final double GRAVITY = .08;
    private static final double AIR_DRAG = .98;

    private final World world;
    private final List<Pad> pads;
    private final Map<UUID, Contact> contacts = new HashMap<>();

    BuildMartJumpPads(World world, List<BuildMartConfig.JumpPadZone> zones) {
        this.world = world;
        pads = zones.stream().map(Pad::new).toList();
    }

    /** Called exactly once per game tick. A fresh contact is confirmed on the following tick. */
    boolean sample(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || player.isGliding() || player.isFlying() || player.isInsideVehicle() || !player.isOnGround()) {
            forget(uuid);
            return false;
        }
        Location location = player.getLocation();
        Pad pad = world.equals(location.getWorld()) ? padAt(location) : null;
        if (pad == null || location.clone().subtract(0, .1, 0).getBlock().isPassable()) {
            forget(uuid);
            return false;
        }
        Contact contact = contacts.get(uuid);
        if (contact != null && contact.pad == pad && contact.launched) return false;
        if (player.getVelocity().getY() > .05) {
            forget(uuid);
            return false;
        }
        if (contact == null || contact.pad != pad) {
            contacts.put(uuid, new Contact(pad));
            return false;
        }
        contact.launched = true;
        player.setVelocity(launchVelocity(location.getYaw(), pad.vertical));
        player.setFallDistance(0);
        return true;
    }

    private Pad padAt(Location location) {
        for (Pad pad : pads) {
            if (pad.vertical > 0 && location.getX() >= pad.minX && location.getX() < pad.maxX
                    && location.getZ() >= pad.minZ && location.getZ() < pad.maxZ
                    && Math.abs(location.getY() - pad.surfaceY) <= .125) return pad;
        }
        return null;
    }

    void forget(UUID uuid) {
        contacts.remove(uuid);
    }

    void clear() {
        contacts.clear();
    }

    static Vector launchVelocity(float yaw, double vertical) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians) * FORWARD_SPEED, vertical, Math.cos(radians) * FORWARD_SPEED);
    }

    /** Solve the vanilla airborne gravity/drag trajectory once for each pad, aiming for Y=180. */
    static double verticalVelocity(double sourceY) {
        double height = TARGET_Y - sourceY;
        if (height <= 0) return 0;
        double low = 0, high = 1;
        while (rise(high) < height) high *= 2;
        for (int iteration = 0; iteration < 48; iteration++) {
            double middle = (low + high) / 2;
            if (rise(middle) < height) low = middle;
            else high = middle;
        }
        return (low + high) / 2;
    }

    private static double rise(double velocity) {
        double height = 0;
        while (velocity > 0) {
            height += velocity;
            velocity = (velocity - GRAVITY) * AIR_DRAG;
        }
        return height;
    }

    private static final class Pad {
        final double minX, maxX, minZ, maxZ, surfaceY, vertical;

        Pad(BuildMartConfig.JumpPadZone zone) {
            Vector min = Vector.getMinimum(zone.pos1(), zone.pos2());
            Vector max = Vector.getMaximum(zone.pos1(), zone.pos2());
            minX = min.getBlockX(); maxX = max.getBlockX() + 1;
            minZ = min.getBlockZ(); maxZ = max.getBlockZ() + 1;
            surfaceY = max.getBlockY() + 1;
            vertical = verticalVelocity(surfaceY);
        }
    }

    private static final class Contact {
        final Pad pad;
        boolean launched;
        Contact(Pad pad) { this.pad = pad; }
    }
}
