package ink.ziip.championshipscore.api.game.config;

import ink.ziip.championshipscore.api.game.acerace.AceRaceConfig;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalConfig;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltConfig;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagConfig;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsConfig;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownConfig;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSConfig;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Resolves the first configured player-facing game spawn for admin/editing teleports. */
public final class GameSpawnResolver {
    private GameSpawnResolver() {
    }

    @Nullable
    public static Location resolve(@NotNull BaseGameConfig config) {
        Location location = switch (config) {
            case BattleBoxConfig c -> first(c.getRightSpawnPoint(), c.getLeftSpawnPoint());
            case ParkourTagConfig c -> first(c.getRightAreaChaserSpawnPoint(), c.getLeftAreaChaserSpawnPoint(),
                    firstString(c.getRightAreaEscapeeSpawnPoints()), firstString(c.getLeftAreaEscapeeSpawnPoints()));
            case SkyWarsConfig c -> firstString(c.getTeamSpawnPoints());
            case TGTTOSConfig c -> tgttosSpawn(c);
            case TNTRunConfig c -> firstString(c.getPlayerSpawnPoints());
            case BuildMartConfig c -> {
                var base = c.getBaseTemplate();
                yield first(c.getSpectatorSpawnPoint(),
                        base == null ? null : base.getPortalPoint(), c.getHubPortalPoint());
            }
            case ParkourWarriorConfig c -> c.getPlayerSpawnPoint();
            case HotyCodyDuskyConfig c -> c.getPlayerSpawnPoint();
            case AceRaceConfig c -> c.getStartSpawnPoint();
            case SnowballShowdownConfig c -> firstSectionLocation(c.getPlayerSpawnPoints());
            case DragonEggCarnivalConfig c -> firstString(c.getRightSpawnPoints(), c.getLeftSpawnPoints());
            case DodgeboltConfig c -> firstString(c.getRightSpawnPoints(), c.getLeftSpawnPoints());
            default -> null;
        };
        Location fallback = location != null ? location : spectator(config);
        if (fallback != null && fallback.getWorld() == null) {
            World world = world(config);
            if (world != null) fallback.setWorld(world);
        }
        return fallback;
    }

    @Nullable
    private static Location tgttosSpawn(@NotNull TGTTOSConfig config) {
        Vector first = config.getPlayerSpawnAreaPos1();
        Vector second = config.getPlayerSpawnAreaPos2();
        if (first == null || second == null) return null;
        World world = world(config);
        if (world == null) return null;
        double x = (Math.min(first.getX(), second.getX()) + Math.max(first.getX(), second.getX())) / 2.0 + 0.5;
        double y = Math.max(first.getY(), second.getY()) + 1.0;
        double z = (Math.min(first.getZ(), second.getZ()) + Math.max(first.getZ(), second.getZ())) / 2.0 + 0.5;
        return new Location(world, x, y, z,
                config.getPlayerSpawnYaw() == null ? 0f : config.getPlayerSpawnYaw(),
                config.getPlayerSpawnPitch() == null ? 0f : config.getPlayerSpawnPitch());
    }

    @Nullable
    private static Location firstSectionLocation(@Nullable ConfigurationSection section) {
        if (section == null) return null;
        for (String key : section.getKeys(false)) {
            List<String> values = section.getStringList(key);
            Location location = firstString(values);
            if (location != null) return location;
            String value = section.getString(key);
            location = parse(value);
            if (location != null) return location;
        }
        return null;
    }

    @SafeVarargs
    @Nullable
    private static Location first(@Nullable Location... locations) {
        if (locations == null) return null;
        for (Location location : locations) if (location != null) return location;
        return null;
    }

    @SafeVarargs
    @Nullable
    private static Location firstString(@Nullable List<String>... lists) {
        if (lists == null) return null;
        for (List<String> list : lists) {
            if (list == null) continue;
            for (String value : list) {
                Location location = parse(value);
                if (location != null) return location;
            }
        }
        return null;
    }

    @Nullable
    private static Location parse(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Utils.getLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static World world(@NotNull BaseGameConfig config) {
        String name = config.getConfiguredWorld();
        return name == null || name.isBlank() ? null : Bukkit.getWorld(name);
    }

    @Nullable
    private static Location spectator(@NotNull BaseGameConfig config) {
        try {
            Object value = config.getClass().getMethod("getSpectatorSpawnPoint").invoke(config);
            return value instanceof Location location ? location : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
