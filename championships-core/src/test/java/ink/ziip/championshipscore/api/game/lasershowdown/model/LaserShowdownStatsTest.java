package ink.ziip.championshipscore.api.game.lasershowdown.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

import org.junit.jupiter.api.Test;

class LaserShowdownStatsTest {
    @Test
    void sumsCombatCountsAndChoosesFavoriteByLives() {
        var first = new LaserShowdownStats(1, 10, 20, 10, 0, 1, 2, 0, 0);
        var second = new LaserShowdownStats(1, 30, 80, 8, 0, 2, 0, 0, 1);
        var sum = first.merge(second);
        assertEquals(2, sum.games());
        assertEquals(40, sum.kills());
        assertEquals(18, sum.headHits());
        assertEquals(100, sum.enemyHits());
        assertEquals(LaserGun.RIFLE, sum.favoriteGun());
        assertEquals(3, sum.lives(LaserGun.RIFLE));
        assertNull(LaserShowdownStats.EMPTY.favoriteGun());
    }
}
