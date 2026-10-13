package ink.ziip.championshipscore.api.game.lasershowdown.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LaserShowdownLifeTest {
    @Test
    void closesSelectionAtExactlySixSecondsWithoutAnOpenMenu() {
        var life = new LaserShowdownLife(50);
        assertTrue(life.canSelect(169));
        assertFalse(life.canSelect(170));
        assertFalse(life.openMenu(170));
        assertTrue(life.locked());
    }

    @Test
    void grantsUnlimitedGraceOnlyToTheAlreadyOpenMenu() {
        var life = new LaserShowdownLife(0);
        assertTrue(life.openMenu(119));
        life.expire(120);
        assertFalse(life.locked());
        assertTrue(life.canSelect(10000));
        assertFalse(life.openMenu(10000));
        life.closeMenu(10000);
        assertTrue(life.locked());
        assertFalse(life.openMenu(10001));
        assertFalse(life.canSelect(10001));
    }

    @Test
    void closingBeforeDeadlineAllowsAnotherOpenButClosingAfterDoesNot() {
        var life = new LaserShowdownLife(100);
        assertTrue(life.openMenu(101));
        life.closeMenu(119);
        assertTrue(life.openMenu(219));
        life.closeMenu(220);
        assertFalse(life.openMenu(221));
    }

    @Test
    void shotOrDamageLocksEvenAnOpenMenuAndCountsThisLifeOnce() {
        var life = new LaserShowdownLife(0);
        assertTrue(life.openMenu(1));
        assertFalse(life.takeUsage());
        life.lock();
        assertFalse(life.canSelect(2));
        assertTrue(life.takeUsage());
        life.lock();
        assertFalse(life.takeUsage());
        life.closeMenu(3);
        assertFalse(life.openMenu(4));
        var nextLife = new LaserShowdownLife(20);
        assertTrue(nextLife.openMenu(21));
        assertFalse(nextLife.locked());
    }
}
