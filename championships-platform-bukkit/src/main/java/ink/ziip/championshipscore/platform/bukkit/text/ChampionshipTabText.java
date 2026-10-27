package ink.ziip.championshipscore.platform.bukkit.text;

import ink.ziip.championshipscore.shared.presentation.MessageTemplate;
import ink.ziip.championshipscore.shared.presentation.PlayerTextFormat;

import net.kyori.adventure.text.Component;

import java.util.Map;

/** Shared configurable player identity, TAB and chat rendering for Core and Bukkit workers. */
public final class ChampionshipTabText {
    private ChampionshipTabText() {}

    public static String bracketedPrefix(String coloredLabel) {
        return bracketedPrefix(PlayerTextFormat.legacy(), coloredLabel);
    }

    public static String bracketedPrefix(PlayerTextFormat format, String coloredLabel) {
        return text(format.prefix(), Map.of("%label%", coloredLabel));
    }

    public static String gamePrefix(String gameName) {
        return gamePrefix(PlayerTextFormat.legacy(), gameName);
    }

    public static String gamePrefix(PlayerTextFormat format, String gameName) {
        return bracketedPrefix(format, text(format.gameLabel(), Map.of("%game%", gameName)));
    }

    public static String teamFooter(String template, String coloredTeamName, double points) {
        return text(
                template,
                Map.of("%team%", coloredTeamName, "%points%", LegacyText.formatPoints(points)));
    }

    public static String currentGameFooter(String template, String gameName) {
        return text(template, Map.of("%game%", gameName));
    }

    public static String dailyTeamFooter(String template, String coloredTeamName) {
        return text(template, Map.of("%team%", coloredTeamName));
    }

    public static String playerNameColor(String teamColorCode, boolean activePlayer) {
        return playerNameColor(PlayerTextFormat.legacy(), teamColorCode, activePlayer);
    }

    /** Team colours only apply to active participants; the inactive colour belongs to the theme. */
    public static String playerNameColor(
            PlayerTextFormat format, String teamColorCode, boolean activePlayer) {
        return LegacyText.translateColorCodes(
                !activePlayer || teamColorCode == null || teamColorCode.isBlank()
                        ? format.inactiveNameColor()
                        : teamColorCode);
    }

    public static String playerIdentity(
            String coloredLabel, String teamColorCode, boolean activePlayer, String playerName) {
        return playerIdentity(
                PlayerTextFormat.legacy(), coloredLabel, teamColorCode, activePlayer, playerName);
    }

    public static String playerIdentity(
            PlayerTextFormat format,
            String coloredLabel,
            String teamColorCode,
            boolean activePlayer,
            String playerName) {
        return text(
                format.identity(),
                Map.of(
                        "%prefix%",
                        bracketedPrefix(format, coloredLabel),
                        "%label%",
                        coloredLabel,
                        "%player%",
                        playerNameColor(format, teamColorCode, activePlayer) + playerName,
                        "%player_name%",
                        playerName));
    }

    public static Component playerIdentityComponent(
            String coloredLabel, String teamColorCode, boolean activePlayer, String playerName) {
        return playerIdentityComponent(
                PlayerTextFormat.legacy(), coloredLabel, teamColorCode, activePlayer, playerName);
    }

    public static Component playerIdentityComponent(
            PlayerTextFormat format,
            String coloredLabel,
            String teamColorCode,
            boolean activePlayer,
            String playerName) {
        return LegacyText.component(
                playerIdentity(format, coloredLabel, teamColorCode, activePlayer, playerName));
    }

    public static Component chatLine(
            String coloredLabel,
            String teamColorCode,
            boolean activePlayer,
            String playerName,
            Component message) {
        return chatLine(
                PlayerTextFormat.legacy(),
                coloredLabel,
                teamColorCode,
                activePlayer,
                playerName,
                message,
                false);
    }

    public static Component dailyChatLine(
            String coloredLabel,
            String teamColorCode,
            boolean activePlayer,
            String playerName,
            Component message) {
        return chatLine(
                PlayerTextFormat.legacy(),
                coloredLabel,
                teamColorCode,
                activePlayer,
                playerName,
                message,
                true);
    }

    public static Component chatLine(
            PlayerTextFormat format,
            String coloredLabel,
            String teamColorCode,
            boolean activePlayer,
            String playerName,
            Component message,
            boolean daily) {
        return ComponentTemplates.render(
                daily ? format.dailyChat() : format.chat(),
                Map.of(
                        "%prefix%", LegacyText.component(bracketedPrefix(format, coloredLabel)),
                        "%identity%",
                                playerIdentityComponent(
                                        format,
                                        coloredLabel,
                                        teamColorCode,
                                        activePlayer,
                                        playerName),
                        "%label%", LegacyText.component(coloredLabel),
                        "%player%",
                                LegacyText.component(
                                        playerNameColor(format, teamColorCode, activePlayer)
                                                + playerName),
                        "%player_name%", Component.text(playerName),
                        "%message%", message));
    }

    private static String text(String template, Map<String, String> values) {
        return LegacyText.translateColorCodes(MessageTemplate.render(template, values));
    }
}
