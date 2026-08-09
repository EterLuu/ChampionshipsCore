package ink.ziip.championshipscore.api.game.skywars;

/** All SkyWars point constants, separated from its mechanics and map geometry. */
public record SkyWarsScoring(int kill, int survive, int playerEliminationSurvival,
                             int teamEliminationSurvival) {
}
