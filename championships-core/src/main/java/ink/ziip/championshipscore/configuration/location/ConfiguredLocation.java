package ink.ziip.championshipscore.configuration.location;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Function;

/** World-independent coordinates; resolving a world is a separate runtime operation. */
public record ConfiguredLocation(
        String world, double x, double y, double z, float yaw, float pitch) {
    public ConfiguredLocation {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Location coordinates and rotation must be finite");
        }
        world = world == null || world.isBlank() ? null : world;
    }

    public static ConfiguredLocation read(Object value) {
        if (value == null) return null;
        if (value instanceof ConfigurationSection section) {
            if (section.contains("==")) {
                throw new IllegalArgumentException(
                        "Location sections must omit the Bukkit '==' marker");
            }
            for (String axis : new String[] {"x", "y", "z"}) {
                if (!(section.get(axis) instanceof Number)) {
                    throw new IllegalArgumentException("Location requires numeric " + axis);
                }
            }
            return new ConfiguredLocation(
                    section.getString("world_key", section.getString("world")),
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    rotation(section, "yaw"),
                    rotation(section, "pitch"));
        }
        if (value instanceof String text) {
            // Split from the right so namespaced world keys retain their colon.
            String[] parts = text.split(":", -1);
            if (parts.length != 6 && parts.length != 7) {
                throw new IllegalArgumentException("Location requires world:x:y:z:yaw:pitch");
            }
            int start = parts.length - 5;
            String world = String.join(":", java.util.Arrays.copyOf(parts, start));
            if (world.isBlank()) throw new IllegalArgumentException("Location requires a world");
            return new ConfiguredLocation(
                    world,
                    Double.parseDouble(parts[start]),
                    Double.parseDouble(parts[start + 1]),
                    Double.parseDouble(parts[start + 2]),
                    Float.parseFloat(parts[start + 3]),
                    Float.parseFloat(parts[start + 4]));
        }
        throw new IllegalArgumentException("Location must be a raw section or coordinate string");
    }

    public Location resolve(Function<String, World> worlds) {
        return new Location(world == null ? null : worlds.apply(world), x, y, z, yaw, pitch);
    }

    public ConfiguredLocation requireWorld(String expected) {
        if (world == null || !world.equals(expected)) {
            throw new IllegalArgumentException(
                    "Location must belong to the configured world: " + expected);
        }
        return this;
    }

    public String asString() {
        if (world == null)
            throw new IllegalStateException(
                    "Location requires a world to write a coordinate string");
        return world + ":" + x + ":" + y + ":" + z + ":" + yaw + ":" + pitch;
    }

    private static float rotation(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (value == null) return 0F;
        if (!(value instanceof Number number))
            throw new IllegalArgumentException("Location requires numeric " + key);
        return number.floatValue();
    }
}
