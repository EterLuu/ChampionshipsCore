package ink.ziip.championshipscore.platform.bukkit.text;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** Player identity metadata shared by Core and Bukkit game workers. */
public record PlayerPresentation(String label, String teamColorCode, boolean activePlayer) {
    public PlayerPresentation {
        Objects.requireNonNull(label, "label");
    }

    public static PlayerPresentation spectator(String label) {
        return new PlayerPresentation(label, null, false);
    }

    public Component identity(String playerName) {
        return ChampionshipTabText.playerIdentityComponent(label, teamColorCode, activePlayer, playerName);
    }

    public Component chatLine(String playerName, Component message) {
        return ChampionshipTabText.chatLine(label, teamColorCode, activePlayer, playerName, message);
    }
}
