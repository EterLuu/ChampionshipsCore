package ink.ziip.championshipscore.util;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {
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

    public static String translateColorCodes(String message) {
        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : ch) {
                builder.append("&").append(c);
            }

            message = message.replace(hexCode, builder.toString());
            matcher = pattern.matcher(message);
        }
        return translateAmpersandCodes(message);
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

    public static Location getLocation(String content) {
        String[] str = content.split(":", 6);
        return new Location(Bukkit.getWorld(str[0]),
                Double.parseDouble(str[1]),
                Double.parseDouble(str[2]),
                Double.parseDouble(str[3]),
                Float.parseFloat(str[4]),
                Float.parseFloat(str[5]));
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

    public static String getMessage(List<String> messages) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String message : messages) {
            stringBuilder.append(translateColorCodes(message)).append('\n');
        }

        return stringBuilder.toString();
    }

    public static void playSoundToAllPlayers(Sound sound, float volume, float pitch) {
        forEachOnlinePlayer(player -> {
            onEntity(player, () -> player.playSound(player.getLocation(), sound, volume, pitch));
        });
    }

    public static void sendMessageToAllPlayers(String message) {
        forEachOnlinePlayer(player -> {
            onEntity(player, () -> player.sendMessage(message));
        });
    }

    public static void changeLevelForAllPlayers(int level) {
        forEachOnlinePlayer(player -> {
            onEntity(player, () -> player.setLevel(Math.abs(level)));
        });
    }

    public static void sendTitleToAllPlayers(String title, String subtitle) {
        Component titleComponent = LegacyComponentSerializer.legacySection().deserialize(title);
        Component subtitleComponent = LegacyComponentSerializer.legacySection().deserialize(subtitle);
        // No fade in/out; stay 20 ticks.
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
        Title titleMessage = Title.title(titleComponent, subtitleComponent, times);
        forEachOnlinePlayer(player -> {
            onEntity(player, () -> player.showTitle(titleMessage));
        });
    }

    public static void runForPlayer(Player player, Runnable action) {
        onEntity(player, action);
    }

    public static void performCommand(Player player, String command) {
        onEntity(player, () -> player.performCommand(command));
    }

    private static void onEntity(Player player, Runnable action) {
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            FoliaScheduler.global(plugin).runEntity(player, action);
        }
    }

    private static void forEachOnlinePlayer(Consumer<Player> action) {
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            FoliaScheduler.global(plugin).runTask(() ->
                    Bukkit.getOnlinePlayers().forEach(action));
        }
    }
}
