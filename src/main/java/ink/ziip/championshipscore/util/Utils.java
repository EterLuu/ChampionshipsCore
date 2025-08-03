package ink.ziip.championshipscore.util;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
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
        return ChatColor.translateAlternateColorCodes('&', message);
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

    public static ChatColor toChatColor(@NotNull String color) {
        if (color.equalsIgnoreCase("green"))
            return ChatColor.DARK_GREEN;
        if (color.equalsIgnoreCase("brown"))
            return ChatColor.DARK_RED;
        if (color.equalsIgnoreCase("lime"))
            return ChatColor.DARK_AQUA;
        if (color.equalsIgnoreCase("pink"))
            return ChatColor.LIGHT_PURPLE;
        if (color.equalsIgnoreCase("light_blue"))
            return ChatColor.AQUA;
        if (color.equalsIgnoreCase("cyan"))
            return ChatColor.GREEN;
        if (color.equalsIgnoreCase("purple"))
            return ChatColor.DARK_PURPLE;
        if (color.equalsIgnoreCase("orange"))
            return ChatColor.GOLD;

        try {
            return ChatColor.valueOf(color);
        } catch (Exception exception) {
            return ChatColor.WHITE;
        }
    }

    public static String[] getColorNames() {
        return new String[]{
                "white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue",
                "brown", "green", "red", "black"
        };
    }

    public static net.md_5.bungee.api.ChatColor toBungeeChatColor(@NotNull String color) {
        if (color.equalsIgnoreCase("green"))
            return net.md_5.bungee.api.ChatColor.DARK_GREEN;
        if (color.equalsIgnoreCase("brown"))
            return net.md_5.bungee.api.ChatColor.DARK_RED;
        if (color.equalsIgnoreCase("lime"))
            return net.md_5.bungee.api.ChatColor.DARK_AQUA;
        if (color.equalsIgnoreCase("pink"))
            return net.md_5.bungee.api.ChatColor.LIGHT_PURPLE;
        if (color.equalsIgnoreCase("light_blue"))
            return net.md_5.bungee.api.ChatColor.AQUA;
        if (color.equalsIgnoreCase("cyan"))
            return net.md_5.bungee.api.ChatColor.GREEN;
        if (color.equalsIgnoreCase("purple"))
            return net.md_5.bungee.api.ChatColor.DARK_PURPLE;
        if (color.equalsIgnoreCase("orange"))
            return net.md_5.bungee.api.ChatColor.GOLD;

        try {
            return net.md_5.bungee.api.ChatColor.of(color);
        } catch (Exception exception) {
            return net.md_5.bungee.api.ChatColor.WHITE;
        }
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public static void sendMessageToAllPlayers(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    public static void changeLevelForAllPlayers(int level) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setLevel(Math.abs(level));
        }
    }

    public static void sendTitleToAllPlayers(String title, String subtitle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, 0, 20, 0);
        }
    }

    public static void sendMessageToAllSpigotPlayers(BaseComponent message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(message);
        }
    }
}
