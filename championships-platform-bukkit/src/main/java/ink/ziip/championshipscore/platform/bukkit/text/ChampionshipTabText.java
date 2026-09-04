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

    /**
     * Prefix used for a player who is currently participating in a game.  The game label has its
     * own colour so it cannot inherit the participant's team colour; the player's name is rendered
     * separately through {@link #playerNameColor(String, boolean)}.
     */
    public static String gamePrefix(String gameName) {
        return bracketedPrefix(LegacyText.translateColorCodes("&#fff566" + gameName));
    }

    public static String teamFooter(String template, String coloredTeamName, double points) {
        return LegacyText.translateColorCodes(template
                .replace("%team%", coloredTeamName)
                .replace("%points%", LegacyText.formatPoints(points)));
    }

    public static String currentGameFooter(String template, String gameName) {
        return LegacyText.translateColorCodes(template.replace("%game%", gameName));
    }

    /** DAILY has no championship points; its TAB footer identifies the temporary colour team only. */
    public static String dailyTeamFooter(String template, String coloredTeamName) {
        return LegacyText.translateColorCodes(template.replace("%team%", coloredTeamName));
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
