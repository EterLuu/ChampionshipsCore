package ink.ziip.championshipscore.api.game.bingo.game;

import net.kyori.adventure.text.format.TextColor;

/**
 * The result of a finished round, attached to the {@link BingoRound} once the game ends so the
 * card-map renderer can paint a win-state overlay during the post-game delay.
 *
 * <p>Decoupled from the team class: it carries only the winning team's id and colour (or a null id on
 * a draw), the two values the renderer needs.
 *
 * @param winnerId    the winning team's id, or null on a draw
 * @param winnerColor the winning team's colour (ignored when {@code winnerId} is null)
 * @param type        how the round was decided — drives the overlay style
 */
public record RoundOutcome(String winnerId, TextColor winnerColor, OutcomeType type) {
    public enum OutcomeType {
        /** A team completed enough bingo lines. The renderer draws scribble lines on the winning lines. */
        LINES,
        /** A team completed every cell on its card. */
        FULL_CARD,
        /** Resolved by most completed tasks (any non-points timeout). */
        MOST_COMPLETED,
        /** Resolved by highest score (points timeout). */
        TOP_SCORE,
        /** No winner — the renderer paints no win overlay. */
        DRAW
    }
}
