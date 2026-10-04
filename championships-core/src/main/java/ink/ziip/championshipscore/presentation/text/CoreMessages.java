package ink.ziip.championshipscore.presentation.text;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Configured Core messages, player identity and server broadcasts. */
public final class CoreMessages {
    private CoreMessages() {}

    public static String dailyMessage(String message) {
        return MessageConfig.DAILY_PREFIXED
                .replace("%message%", message)
                .replace("%prefix%", MessageConfig.DAILY_PREFIX);
    }

    /**
     * Neutral player name followed by the player's coloured team, matching the server chat
     * identity.
     */
    public static String formatPlayerName(@NotNull Player player) {
        ChampionshipTeam team =
                ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
        return formatPlayerName(player.getName(), team);
    }

    /** Offline-safe player identity for messages which only have a UUID. */
    public static String formatPlayerName(@NotNull UUID uuid) {
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        return formatPlayerName(
                plugin.getPlayerManager().getPlayerName(uuid),
                plugin.getTeamManager().getTeamByPlayer(uuid));
    }

    /** Resolves a possibly offline player's team without creating or changing player data. */
    public static String formatPlayerName(@NotNull String name) {
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        Player online = Bukkit.getPlayerExact(name);
        ChampionshipTeam team =
                online == null ? null : plugin.getTeamManager().getTeamByPlayer(online);
        if (team == null) {
            for (ChampionshipTeam candidate : plugin.getTeamManager().getTeamList()) {
                boolean member =
                        candidate.getMembers().stream()
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
        if (team != null) identity += " &7<" + team.getColoredName() + "&7>";
        return LegacyText.translateColorCodes(identity);
    }

    /** Neutral player name for messages which already display the team separately. */
    public static String formatPlayerNameOnly(@NotNull String name) {
        return LegacyText.translateColorCodes("&f" + name);
    }

    public static String getMessage(List<String> messages) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String message : messages) {
            stringBuilder.append(LegacyText.translateColorCodes(message)).append('\n');
        }

        return stringBuilder.toString();
    }

    public static void playSoundToAllPlayers(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public static void sendMessageToAllPlayers(String message) {
        String translated = LegacyText.translateColorCodes(message);
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
        return LegacyText.translateColorCodes("&#bababa[&#fff566管理&#bababa] &#ededed" + message);
    }

    public static String formatAdminInfo(String message) {
        return LegacyText.translateColorCodes("&#bababa[&#fff566管理&#bababa] &#bababa" + message);
    }

    public static String formatAdminError(String message) {
        return LegacyText.translateColorCodes("&#bababa[&#ff6b26管理&#bababa] &#ededed" + message);
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(LegacyText.component(message));
    }

    public static void sendActionBarToAllPlayers(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendActionBar(player, message);
        }
    }

    public static void sendTitleToAllPlayers(String title, String subtitle) {
        sendTitleToAllPlayers(title, subtitle, 20);
    }

    public static void sendTitleToAllPlayers(String title, String subtitle, int stayTicks) {
        // Use the shared serializer so &#RRGGBB values retain their full six-digit colour
        // when the title is emitted outside a game instance (event round settlement, voting, etc.).
        Component titleComponent = LegacyText.component(title);
        Component subtitleComponent = LegacyText.component(subtitle);
        Title.Times times =
                Title.Times.times(Duration.ZERO, Duration.ofMillis(stayTicks * 50L), Duration.ZERO);
        Title titleMessage = Title.title(titleComponent, subtitleComponent, times);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(titleMessage);
        }
    }
}
