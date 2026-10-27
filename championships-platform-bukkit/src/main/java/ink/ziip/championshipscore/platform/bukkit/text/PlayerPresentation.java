package ink.ziip.championshipscore.platform.bukkit.text;

import ink.ziip.championshipscore.shared.presentation.PlayerTextFormat;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** Immutable identity metadata and the effective Core-owned templates for one presentation. */
public record PlayerPresentation(
        String label,
        String teamColorCode,
        boolean activePlayer,
        boolean daily,
        PlayerTextFormat format) {
    public PlayerPresentation {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(format, "format");
    }

    public PlayerPresentation(
            String label, String teamColorCode, boolean activePlayer, boolean daily) {
        this(label, teamColorCode, activePlayer, daily, PlayerTextFormat.legacy());
    }

    public PlayerPresentation(String label, String teamColorCode, boolean activePlayer) {
        this(label, teamColorCode, activePlayer, false);
    }

    public static PlayerPresentation spectator(String label) {
        return new PlayerPresentation(label, null, false);
    }

    public Component identity(String playerName) {
        return ChampionshipTabText.playerIdentityComponent(
                format, label, teamColorCode, activePlayer, playerName);
    }

    public Component chatLine(String playerName, Component message) {
        return ChampionshipTabText.chatLine(
                format, label, teamColorCode, activePlayer, playerName, message, daily);
    }
}
