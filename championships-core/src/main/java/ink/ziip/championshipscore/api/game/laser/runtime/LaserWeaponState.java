package ink.ziip.championshipscore.api.game.laser.runtime;

import ink.ziip.championshipscore.api.game.laser.mechanics.LaserGunMechanics;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One match's weapon state, keyed by player identity. The game owns the clock, eligibility and
 * respawn rules; this component owns magazines, fire deadlines, bursts and interrupted reloads.
 * Callers must still use their existing entity-thread and current-session guards for Bukkit IO.
 */
public final class LaserWeaponState implements AutoCloseable {
    public enum FireResult {
        FIRED,
        COOLDOWN,
        RELOADING,
        NOT_READY
    }

    private static final class State {
        Double nextShot;
        Integer lastShot;
        int burst;
        Integer ammo;
        LaserGun reloadGun;
        int reloadDeadline;
    }

    private final Map<UUID, State> players = new HashMap<>();
    private boolean closed;

    public synchronized FireResult shoot(UUID player, LaserGun gun, int tick, boolean eligible) {
        if (closed || !eligible || gun == null) return FireResult.NOT_READY;
        completeReload(player, gun, tick);
        State state = players.computeIfAbsent(player, ignored -> new State());
        if (state.reloadGun != null) return FireResult.RELOADING;
        int now = tick * 4;
        if (state.nextShot != null && state.nextShot - tick > 1.0E-9D) return FireResult.COOLDOWN;
        int ammo = state.ammo == null ? gun.magazine() : state.ammo;
        if (ammo <= 0) {
            requestReload(player, gun, tick, eligible);
            return FireResult.RELOADING;
        }
        state.nextShot = LaserGunMechanics.nextShot(tick, state.nextShot, gun);
        state.burst = LaserGunMechanics.burstShot(now, state.lastShot, state.burst);
        state.lastShot = now;
        state.ammo = ammo - 1;
        if (ammo == 1) requestReload(player, gun, tick, eligible);
        return FireResult.FIRED;
    }

    public synchronized boolean requestReload(
            UUID player, LaserGun gun, int tick, boolean eligible) {
        if (closed
                || !eligible
                || gun == null
                || reloading(player)
                || ammo(player, gun) >= gun.magazine()) return false;
        State state = players.computeIfAbsent(player, ignored -> new State());
        state.reloadGun = gun;
        state.reloadDeadline = tick + gun.reloadTicks();
        return true;
    }

    public synchronized boolean completeReload(UUID player, LaserGun heldGun, int tick) {
        State state = players.get(player);
        if (closed
                || heldGun == null
                || state == null
                || state.reloadGun != heldGun
                || state.reloadDeadline > tick) return false;
        state.reloadGun = null;
        state.ammo = heldGun.magazine();
        return true;
    }

    public synchronized boolean reloading(UUID player) {
        return reloadGun(player) != null;
    }

    public synchronized LaserGun reloadGun(UUID player) {
        State state = players.get(player);
        return state == null ? null : state.reloadGun;
    }

    public synchronized LaserGun cancelReload(UUID player) {
        State state = players.get(player);
        if (state == null) return null;
        LaserGun gun = state.reloadGun;
        state.reloadGun = null;
        return gun;
    }

    public synchronized int reloadRemaining(UUID player, int tick) {
        State state = players.get(player);
        return state == null || state.reloadGun == null
                ? 0
                : Math.max(0, state.reloadDeadline - tick);
    }

    public synchronized int ammo(UUID player, LaserGun gun) {
        State state = players.get(player);
        return state == null || state.ammo == null ? gun.magazine() : state.ammo;
    }

    public synchronized int burstShot(UUID player) {
        State state = players.get(player);
        return state == null ? 0 : state.burst;
    }

    /** Equipping changes the magazine while retaining that life's existing shot deadline. */
    public synchronized void equip(UUID player, LaserGun gun) {
        setAmmo(player, gun.magazine());
        cancelReload(player);
    }

    /** Restores a preserved magazine when reconnecting within the same life. */
    public synchronized void setAmmo(UUID player, int ammo) {
        if (!closed)
            players.computeIfAbsent(player, ignored -> new State()).ammo = Math.max(0, ammo);
    }

    /** A new life or departed player must not retain a reload, burst or shot deadline. */
    public synchronized void forget(UUID player) {
        players.remove(player);
    }

    /** Terminal for this match; late calls cannot recreate weapon state. */
    @Override
    public synchronized void close() {
        closed = true;
        players.clear();
    }
}
