package ink.ziip.championshipscore.api.object.game.parkourwarrior;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

@Getter
@Builder
@AllArgsConstructor
public class CCSelection {
    private Vector pos1;
    private Vector pos2;
    private World world;

    public boolean isInside(Location location) {
        return location.getX() >= Math.min(pos1.getX(), pos2.getX()) &&
               location.getX() <= Math.max(pos1.getX(), pos2.getX()) &&
               location.getY() >= Math.min(pos1.getY(), pos2.getY()) &&
               location.getY() <= Math.max(pos1.getY(), pos2.getY()) &&
               location.getZ() >= Math.min(pos1.getZ(), pos2.getZ()) &&
               location.getZ() <= Math.max(pos1.getZ(), pos2.getZ());
    }

    public Location getLocation() {
        return new Location(
            world,
            (pos1.getX() + pos2.getX()) / 2,
            (pos1.getY() + pos2.getY()) / 2,
            (pos1.getZ() + pos2.getZ()) / 2
        );
    }
}
