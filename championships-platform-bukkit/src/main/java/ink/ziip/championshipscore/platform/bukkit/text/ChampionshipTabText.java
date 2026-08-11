package ink.ziip.championshipscore.platform.bukkit.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Shared TAB presentation contract used by the Core server and the Bingo worker. */
public final class ChampionshipTabText {
    private ChampionshipTabText() {
    }

    public static String bracketedPrefix(String coloredLabel) {
        return LegacyText.translateColorCodes("&8[" + coloredLabel + "&8]&r ");
    }

    public static String teamFooter(String coloredTeamName, double points) {
        return LegacyText.translateColorCodes("&f队伍: " + coloredTeamName + " &f| 积分: "
                + LegacyText.formatPoints(points));
    }

    public static String currentGameFooter(String gameName) {
        return LegacyText.translateColorCodes("&f当前游戏: &b" + gameName);
    }

    /** Returns explicit white outside a game so scoreboard or parent-component colours cannot leak in. */
    public static String playerNameColor(String teamColorCode, boolean activePlayer) {
        if (!activePlayer || teamColorCode == null || teamColorCode.isBlank()) {
            return LegacyText.translateColorCodes("&f");
        }
        return LegacyText.translateColorCodes(teamColorCode);
    }

    /** The exact identity shown by TAB, reusable by chat and join/quit messages. */
    public static String playerIdentity(String coloredLabel, String teamColorCode,
                                        boolean activePlayer, String playerName) {
        return bracketedPrefix(coloredLabel) + playerNameColor(teamColorCode, activePlayer)
                + playerName + LegacyText.translateColorCodes("&r");
    }

    public static Component playerIdentityComponent(String coloredLabel, String teamColorCode,
                                                    boolean activePlayer, String playerName) {
        return LegacyText.component(playerIdentity(coloredLabel, teamColorCode, activePlayer, playerName));
    }

    public static Component chatLine(String coloredLabel, String teamColorCode, boolean activePlayer,
                                     String playerName, Component message) {
        return playerIdentityComponent(coloredLabel, teamColorCode, activePlayer, playerName)
                .append(Component.text(" » ", TextColor.color(0x696969)))
                .append(message.colorIfAbsent(TextColor.color(0xededed)));
    }
}
