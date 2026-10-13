package ink.ziip.championshipscore.api.game.lasershowdown.model;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

/** Additive combat counters. Ratios use summed bullet counts, and weapon use counts lives. */
public record LaserShowdownStats(
        long games,
        long kills,
        long enemyHits,
        long headHits,
        long sniperLives,
        long rifleLives,
        long smgLives,
        long machineLives,
        long shotgunLives) {
    public static final LaserShowdownStats EMPTY =
            new LaserShowdownStats(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public LaserShowdownStats merge(LaserShowdownStats other) {
        return new LaserShowdownStats(
                games + other.games,
                kills + other.kills,
                enemyHits + other.enemyHits,
                headHits + other.headHits,
                sniperLives + other.sniperLives,
                rifleLives + other.rifleLives,
                smgLives + other.smgLives,
                machineLives + other.machineLives,
                shotgunLives + other.shotgunLives);
    }

    public long lives(LaserGun gun) {
        return switch (gun) {
            case SNIPER -> sniperLives;
            case RIFLE -> rifleLives;
            case SMG -> smgLives;
            case MACHINE -> machineLives;
            case SHOTGUN -> shotgunLives;
        };
    }

    /** Stable enum order resolves equal life counts without inventing a numeric weapon ranking. */
    public LaserGun favoriteGun() {
        LaserGun favorite = null;
        for (LaserGun gun : LaserGun.values())
            if (lives(gun) > 0 && (favorite == null || lives(gun) > lives(favorite)))
                favorite = gun;
        return favorite;
    }
}
