package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Holds the starting column while allowing gravity to settle a scattered player onto the ground. */
public final class BingoCountdownMovement {
    private BingoCountdownMovement() { }

    public static void constrain(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        event.setTo(destination(event.getFrom(), event.getTo()));
    }

    static Location destination(Location from, Location to) {
        Location settled = to.clone();
        settled.setX(from.getX());
        settled.setZ(from.getZ());
        settled.setY(Math.min(from.getY(), to.getY()));
        return settled;
    }
}
