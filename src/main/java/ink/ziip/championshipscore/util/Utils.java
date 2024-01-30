package ink.ziip.championshipscore.util;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private Utils() {
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

    public static String getCurrentTimeString() {
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return currentTime.format(formatter);
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

    public static void sendMessageToAllSpigotPlayers(BaseComponent message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(message);
        }
    }
}
