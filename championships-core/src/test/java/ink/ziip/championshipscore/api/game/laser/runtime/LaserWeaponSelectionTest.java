package ink.ziip.championshipscore.api.game.laser.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LaserWeaponSelectionTest {
    @Test
    void closesSelectionAtExactlyEightSecondsWithoutAnOpenMenu() {
        var life = new LaserWeaponSelection(50);
        assertTrue(life.canSelect(209));
        assertFalse(life.canSelect(210));
        assertFalse(life.openMenu(210));
        assertTrue(life.locked());
    }

    @Test
    void grantsUnlimitedGraceOnlyToTheAlreadyOpenMenu() {
        var life = new LaserWeaponSelection(0);
        assertTrue(life.openMenu(159));
        life.expire(160);
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
        var life = new LaserWeaponSelection(100);
        assertTrue(life.openMenu(101));
        life.closeMenu(159);
        assertTrue(life.openMenu(259));
        life.closeMenu(260);
        assertFalse(life.openMenu(261));
    }

    @Test
    void shotOrDamageLocksEvenAnOpenMenuAndCountsThisLifeOnce() {
        var life = new LaserWeaponSelection(0);
        assertTrue(life.openMenu(1));
        assertFalse(life.takeUsage());
        life.lock();
        assertFalse(life.canSelect(2));
        assertTrue(life.takeUsage());
        life.lock();
        assertFalse(life.takeUsage());
        life.closeMenu(3);
        assertFalse(life.openMenu(4));
        var nextLife = new LaserWeaponSelection(20);
        assertTrue(nextLife.openMenu(21));
        assertFalse(nextLife.locked());
    }
}
