package ink.ziip.championshipscore.api.game.bingo.util;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapts CC's dynamic {@link ChampionshipTeam} to the two values the ported bingo mechanics need from
 * a team: a stable string id (used to key per-team completions on a {@code GameTask}) and an Adventure
 * {@link TextColor} (used to tint the card map and chest GUI).
 *
 * <p>minebingo modelled teams as a fixed enum carrying an {@code id()} and {@code textColor()}; CC
 * teams are created at runtime from the database, so these are derived here instead. The id is the
 * team name (CC's {@link ChampionshipTeam} equals/hashCode are keyed on the name, so it is unique and
 * stable); the colour is parsed from the team's {@code #RRGGBB} colour code.
 */
public final class BingoTeamAdapter {
    private BingoTeamAdapter() {
    }

    /** Stable per-team id used to key completions. */
    public static String id(@NotNull ChampionshipTeam team) {
        return team.getName();
    }

    /** Free-form RGB colour for the team's card-map fills and chat names, defaulting to white. */
    public static TextColor color(@Nullable ChampionshipTeam team) {
        if (team == null) return NamedTextColor.WHITE;
        TextColor color = TextColor.fromHexString(team.getColorCode());
        return color != null ? color : NamedTextColor.WHITE;
    }
}
