package ink.ziip.championshipscore.platform.bukkit.bingo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class BingoCountdownMovementTest {
    @Test
    void permitsFallingFromScatterHeightEvenWithHorizontalInput() {
        Location from = new Location(null, 10.5, 80.01, -4.5);
        Location to = new Location(null, 11, 79.9, -4, 90, 30);
        Location result = BingoCountdownMovement.destination(from, to);
        assertEquals(10.5, result.getX());
        assertEquals(-4.5, result.getZ());
        assertEquals(79.9, result.getY());
        assertEquals(90, result.getYaw());
        assertEquals(30, result.getPitch());
        assertEquals(11, to.getX());
    }

    @Test
    void preventsJumpingOrWalkingBeforeStart() {
        Location from = new Location(null, 10.5, 80, -4.5);
        Location result =
                BingoCountdownMovement.destination(from, new Location(null, 11, 80.42, -4));
        assertEquals(from.getX(), result.getX());
        assertEquals(from.getY(), result.getY());
        assertEquals(from.getZ(), result.getZ());
    }
}
