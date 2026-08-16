package ink.ziip.championshipscore.util;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {
    private Utils() {
    }

    /** Translates the preferred {@code &#RRGGBB} syntax and the legacy {@code #RRGGBB} syntax. */
    public static String translateColorCodes(String message) {
        return LegacyText.translateColorCodes(message);
    }

    /** Strips section-sign colour/format codes (incl. {@code §x} hex) from a string, for plain-text logging. */
    public static String stripColorCodes(String message) {
        if (message == null) return null;
        return message.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    /** Colour-translates a legacy string and parses it into an Adventure component. */
    public static Component toComponent(String message) {
        return LegacyText.component(message);
    }

    /** Neutral player name followed by the player's coloured team, matching the server chat identity. */
    public static String formatPlayerName(@NotNull Player player) {
        ChampionshipTeam team = ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
        return formatPlayerName(player.getName(), team);
    }

    /** Offline-safe player identity for messages which only have a UUID. */
    public static String formatPlayerName(@NotNull UUID uuid) {
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        return formatPlayerName(plugin.getPlayerManager().getPlayerName(uuid),
                plugin.getTeamManager().getTeamByPlayer(uuid));
    }

    /** Resolves a possibly offline player's team without creating or changing player data. */
    public static String formatPlayerName(@NotNull String name) {
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        Player online = Bukkit.getPlayerExact(name);
        ChampionshipTeam team = online == null ? null : plugin.getTeamManager().getTeamByPlayer(online);
        if (team == null) {
            for (ChampionshipTeam candidate : plugin.getTeamManager().getTeamList()) {
                boolean member = candidate.getMembers().stream()
                        .map(plugin.getPlayerManager()::getPlayerName)
                        .anyMatch(name::equalsIgnoreCase);
                if (member) {
                    team = candidate;
                    break;
                }
            }
        }
        return formatPlayerName(name, team);
    }

    /** Formats a known player/team pair without applying the team colour to the player's name. */
    public static String formatPlayerName(@NotNull String name, @Nullable ChampionshipTeam team) {
        String identity = "&f" + name;
        if (team != null)
            identity += " &7<" + team.getColoredName() + "&7>";
        return translateColorCodes(identity);
    }

    /** Neutral player name for messages which already display the team separately. */
    public static String formatPlayerNameOnly(@NotNull String name) {
        return translateColorCodes("&f" + name);
    }

    public static Location getLocation(String content) {
        String[] str = content.split(":", 6);
        return new Location(Bukkit.getWorld(str[0]),
                Double.parseDouble(str[1]),
                Double.parseDouble(str[2]),
                Double.parseDouble(str[3]),
                Float.parseFloat(str[4]),
                Float.parseFloat(str[5]));
    }

    /**
     * Reads a Location stored as a raw config section (world/world_key + x/y/z/yaw/pitch) without the
     * '==: Location' marker Bukkit uses to auto-deserialize. The world may be unresolved at load time;
     * it is left null rather than throwing (teleports will then fail with a clear "world is null").
     */
    public static Location getLocation(ConfigurationSection section) {
        if (section == null) return null;
        World world = null;
        if (section.contains("world_key")) {
            world = Bukkit.getWorld(NamespacedKey.fromString(section.getString("world_key")));
        } else if (section.contains("world")) {
            world = Bukkit.getWorld(section.getString("world"));
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public static String getLocationConfigString(Location location) {
        if (location.getWorld() != null)
            return location.getWorld().getName() +
                    ":" +
                    location.getX() +
                    ":" +
                    location.getY() +
                    ":" +
                    location.getZ() +
                    ":" +
                    location.getYaw() +
                    ":" +
                    location.getPitch();
        return "";
    }

    /** Horizontal scatter radius (blocks) around the lobby spawn used to spread returning players apart. */
    private static final double LOBBY_SCATTER_RADIUS = 5.0D;

    /**
     * Lobby spawn scattered horizontally around the configured centre for one player. The player's UUID
     * selects a stable golden-angle offset, so a crowd returning from a finished match spreads over the
     * surrounding floor instead of stacking on one point and pushing each other apart. Offsets that land
     * on walls or off the floor shrink progressively back to the exact centre.
     */
    public static Location getScatteredLobbyLocation(@Nullable Location lobby, @NotNull Player player) {
        if (lobby == null || lobby.getWorld() == null)
            return lobby;
        int hash = player.getUniqueId().hashCode();
        double angle = ((hash & 0xFFFF) / 65536.0D) * Math.PI * 2.0D;
        double radius = LOBBY_SCATTER_RADIUS * Math.sqrt(((hash >>> 16) & 0xFFFF) / 65536.0D);
        for (double scale = 1.0D; scale >= 0.2D; scale *= 0.5D) {
            Location candidate = lobby.clone();
            candidate.setX(lobby.getX() + Math.cos(angle) * radius * scale);
            candidate.setZ(lobby.getZ() + Math.sin(angle) * radius * scale);
            if (isSafeLobbySpot(candidate))
                return candidate;
        }
        return lobby;
    }

    /** Solid ground with passable feet and head space, so a scattered player neither falls nor suffocates. */
    private static boolean isSafeLobbySpot(@NotNull Location spot) {
        World world = spot.getWorld();
        int x = spot.getBlockX();
        int y = spot.getBlockY();
        int z = spot.getBlockZ();
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && world.getBlockAt(x, y, z).isPassable()
                && world.getBlockAt(x, y + 1, z).isPassable();
    }

    /**
     * Aligns a player-captured point with the horizontal centre of the block they occupy. Height and
     * view direction deliberately remain untouched, because spawn surfaces may be slabs or otherwise
     * sit between whole Y coordinates.
     */
    public static Location centerOnBlock(@NotNull Location location) {
        Location centered = location.clone();
        centered.setX(location.getBlockX() + 0.5D);
        centered.setZ(location.getBlockZ() + 0.5D);
        return centered;
    }

    public static Color hex2rgb(String hexColor) {
        try {
            return Color.fromBGR(
                    Integer.valueOf(hexColor.substring(5, 7), 16),
                    Integer.valueOf(hexColor.substring(3, 5), 16),
                    Integer.valueOf(hexColor.substring(1, 3), 16));
        } catch (Exception ignored) {
            return Color.fromBGR(0, 0, 0);
        }
    }

    /**
     * Resolves a team colour name to its Adventure text colour. The special cases remap Minecraft wool/dye
     * names to the nearest chat colour (e.g. {@code GREEN} wool → dark green); any other name is matched
     * against the 16 named colours directly, falling back to white.
     */
    public static NamedTextColor toNamedTextColor(@NotNull String color) {
        switch (color.toLowerCase(Locale.ROOT)) {
            case "green": return NamedTextColor.DARK_GREEN;
            case "brown": return NamedTextColor.DARK_RED;
            case "lime": return NamedTextColor.DARK_AQUA;
            case "pink":
            case "magenta": return NamedTextColor.LIGHT_PURPLE;
            case "light_blue": return NamedTextColor.AQUA;
            case "cyan": return NamedTextColor.GREEN;
            case "purple": return NamedTextColor.DARK_PURPLE;
            case "orange": return NamedTextColor.GOLD;
            case "black":
            case "gray": return NamedTextColor.DARK_GRAY;
            case "light_gray": return NamedTextColor.GRAY;
            default:
                NamedTextColor named = NamedTextColor.NAMES.value(color.toLowerCase(Locale.ROOT));
                return named != null ? named : NamedTextColor.WHITE;
        }
    }

    /** Uses an exact Minecraft named colour from the configured hex code when one exists. */
    public static NamedTextColor toNamedTextColor(@NotNull String color, @Nullable String colorCode) {
        TextColor parsed = colorCode == null ? null : TextColor.fromHexString(colorCode);
        if (parsed != null) {
            NamedTextColor exact = NamedTextColor.namedColor(parsed.value());
            if (exact != null) return exact;
        }
        return toNamedTextColor(color);
    }

    public static String[] getColorNames() {
        return new String[]{
                "white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"
        };
    }

    public static UUID getPlayerUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8));
    }

    public static String getCurrentTimeString() {
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return currentTime.format(formatter);
    }

    public static String formatPoints(double points) {
        return LegacyText.formatPoints(points);
    }

    public static String getMessage(List<String> messages) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String message : messages) {
            stringBuilder.append(translateColorCodes(message)).append('\n');
        }

        return stringBuilder.toString();
    }

    public static void playSoundToAllPlayers(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public static void sendMessageToAllPlayers(String message) {
        String translated = translateColorCodes(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(translated);
        }
    }

    public static void sendAdminSuccess(CommandSender sender, String message) {
        sender.sendMessage(formatAdminSuccess(message));
    }

    public static void sendAdminInfo(CommandSender sender, String message) {
        sender.sendMessage(formatAdminInfo(message));
    }

    public static void sendAdminError(CommandSender sender, String message) {
        sender.sendMessage(formatAdminError(message));
    }

    public static String formatAdminSuccess(String message) {
        return translateColorCodes("&#bababa[&#fff566管理&#bababa] &#ededed" + message);
    }

    public static String formatAdminInfo(String message) {
        return translateColorCodes("&#bababa[&#fff566管理&#bababa] &#bababa" + message);
    }

    public static String formatAdminError(String message) {
        return translateColorCodes("&#bababa[&#ff6b26管理&#bababa] &#ededed" + message);
    }

    public static String formatGameLog(GameTypeEnum gameType, String area, String stage,
                                       String event, String message) {
        String game = gameType == null ? "-" : gameType.toString();
        return "[" + plainLogValue(game) + " - " + plainLogValue(area) + "] "
                + plainLogValue(stage) + " · " + plainLogValue(event) + " | " + plainLogValue(message);
    }

    public static String formatModuleLog(String module, String event, String message) {
        return "[" + plainLogValue(module) + "] " + plainLogValue(event) + " | " + plainLogValue(message);
    }

    private static String plainLogValue(String value) {
        if (value == null || value.isBlank()) return "-";
        return stripColorCodes(translateColorCodes(value));
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize(translateColorCodes(message)));
    }

    public static void sendActionBarToAllPlayers(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendActionBar(player, message);
        }
    }

    public static void changeLevelForAllPlayers(int level) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setLevel(Math.abs(level));
        }
    }

    public static void sendTitleToAllPlayers(String title, String subtitle) {
        sendTitleToAllPlayers(title, subtitle, 20);
    }

    public static void sendTitleToAllPlayers(String title, String subtitle, int stayTicks) {
        Component titleComponent = LegacyComponentSerializer.legacySection().deserialize(translateColorCodes(title));
        Component subtitleComponent = LegacyComponentSerializer.legacySection().deserialize(translateColorCodes(subtitle));
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(stayTicks * 50L), Duration.ZERO);
        Title titleMessage = Title.title(titleComponent, subtitleComponent, times);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(titleMessage);
        }
    }
}
