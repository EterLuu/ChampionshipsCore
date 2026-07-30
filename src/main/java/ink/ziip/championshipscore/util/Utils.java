package ink.ziip.championshipscore.util;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {
    private static final Pattern EXPLICIT_HEX_COLOR = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Pattern LEGACY_HEX_COLOR = Pattern.compile("(?<!&)#([a-fA-F0-9]{6})");

    private Utils() {
    }

    public static void revokeAllAdvancements(Player player) {
        Iterator<Advancement> advancements = Bukkit.getServer().advancementIterator();
        while (advancements.hasNext()) {
            AdvancementProgress progress = player.getAdvancementProgress(advancements.next());
            for (String s : progress.getAwardedCriteria())
                progress.revokeCriteria(s);
        }
    }

    /** Translates the preferred {@code &#RRGGBB} syntax and the legacy {@code #RRGGBB} syntax. */
    public static String translateColorCodes(String message) {
        message = expandHexColors(message, EXPLICIT_HEX_COLOR);
        message = expandHexColors(message, LEGACY_HEX_COLOR);
        return translateAmpersandCodes(message);
    }

    private static String expandHexColors(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char digit : hex.toCharArray()) {
                replacement.append('&').append(digit);
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    /** The color/format code characters accepted after {@code &}, including {@code x} for hex sequences. */
    private static final String COLOR_CODE_CHARS = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final char SECTION = '§';

    /** Replaces {@code &<code>} with the section-sign form the client renders. */
    private static String translateAmpersandCodes(String message) {
        char[] chars = message.toCharArray();
        for (int i = 0; i + 1 < chars.length; i++) {
            if (chars[i] == '&' && COLOR_CODE_CHARS.indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /** Strips section-sign colour/format codes (incl. {@code §x} hex) from a string, for plain-text logging. */
    public static String stripColorCodes(String message) {
        if (message == null) return null;
        return message.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    /** Colour-translates a legacy string and parses it into an Adventure component. */
    public static Component toComponent(String message) {
        return LegacyComponentSerializer.legacySection().deserialize(translateColorCodes(message));
    }

    public static List<String> getOnlinePlayerNames() {
        List<String> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(player.getName());
        }
        return players;
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
        return BigDecimal.valueOf(points).setScale(0, RoundingMode.HALF_UP).toPlainString();
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
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
        return "[游戏:" + plainLogValue(game) + "] [场地:" + plainLogValue(area) + "] [阶段:"
                + plainLogValue(stage) + "] [事件:" + plainLogValue(event) + "] " + plainLogValue(message);
    }

    public static String formatModuleLog(String module, String event, String message) {
        return "[模块:" + plainLogValue(module) + "] [事件:" + plainLogValue(event) + "] "
                + plainLogValue(message);
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
