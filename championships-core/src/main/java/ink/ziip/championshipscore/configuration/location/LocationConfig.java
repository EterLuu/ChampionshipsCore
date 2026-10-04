package ink.ziip.championshipscore.configuration.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** The single conversion boundary between persisted coordinates and Bukkit runtime locations. */
public final class LocationConfig {
    private LocationConfig() {}

    public static Location readLocation(Object value) {
        return readLocation(value, Bukkit.getServer());
    }

    public static Location readLocation(Object value, Server server) {
        ConfiguredLocation coordinates = ConfiguredLocation.read(value);
        return coordinates == null
                ? null
                : coordinates.resolve(identifier -> resolveWorld(server, identifier));
    }

    public static World resolveWorld(Server server, String identifier) {
        if (!identifier.contains(":")) return server.getWorld(identifier);
        NamespacedKey key = NamespacedKey.fromString(identifier);
        if (key == null) throw new IllegalArgumentException("Invalid world key: " + identifier);
        return server.getWorld(key);
    }

    public static String asString(Location location) {
        return capture(location, null).asString();
    }

    /** Writes a raw section, retaining its configured world if that world has not loaded yet. */
    public static void write(ConfigurationSection parent, String path, Location location) {
        ConfiguredLocation previous = ConfiguredLocation.read(parent.get(path));
        ConfiguredLocation coordinates =
                location == null
                        ? null
                        : capture(location, previous == null ? null : previous.world());
        parent.set(path, null);
        if (coordinates == null) return;
        ConfigurationSection section = parent.createSection(path);
        if (coordinates.world() != null) {
            section.set(
                    coordinates.world().contains(":") ? "world_key" : "world", coordinates.world());
        }
        section.set("x", coordinates.x());
        section.set("y", coordinates.y());
        section.set("z", coordinates.z());
        section.set("yaw", coordinates.yaw());
        section.set("pitch", coordinates.pitch());
    }

    private static ConfiguredLocation capture(Location location, String unresolvedWorld) {
        String world =
                location.getWorld() == null ? unresolvedWorld : location.getWorld().getName();
        return new ConfiguredLocation(
                world,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }
}
