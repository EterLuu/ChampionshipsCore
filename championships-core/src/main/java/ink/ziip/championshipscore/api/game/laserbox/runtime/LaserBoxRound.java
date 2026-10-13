package ink.ziip.championshipscore.api.game.laserbox.runtime;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure match state. Tick deadlines prevent cooldown/respawn races across disconnects. */
final class LaserBoxRound {
    private static final int RESPAWN_TICKS = 80;
    static final int INITIAL_SCORE = 50;
    static final int INITIAL_TRANSFER = 5;
    private static final int TOTAL_SCORE = INITIAL_SCORE * 2;
    private int right = INITIAL_SCORE;
    private int tick;
    private boolean finished;
    private final LaserWeaponState weapons = new LaserWeaponState();
    private final Map<UUID, Double> health = new HashMap<>();
    private final Map<UUID, Integer> respawns = new HashMap<>();
    private final Map<UUID, Integer> shields = new HashMap<>();
    private final Map<UUID, Integer> protection = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

    enum DamageResult {
        IGNORED,
        SHIELD,
        DAMAGED,
        ELIMINATED
    }

    private static final double MAX_HEALTH = 100.0D;

    int right() {
        return right;
    }

    int left() {
        return TOTAL_SCORE - right;
    }

    int tick() {
        return tick;
    }

    void advance() {
        if (!finished) tick++;
    }

    /** Kill transfers increase every 30 seconds in the 90-second round. */
    int transfer() {
        if (tick < 600) return INITIAL_TRANSFER; // 0:00-0:30
        if (tick < 1200) return 7; // 0:30-1:00
        return 10; // 1:00-1:30
    }

    boolean finished() {
        return finished;
    }

    boolean finish() {
        if (finished) return false;
        finished = true;
        weapons.close();
        return true;
    }

    boolean ready(UUID player) {
        return !finished && !respawns.containsKey(player);
    }

    LaserWeaponState.FireResult shoot(UUID player, LaserGun gun) {
        return weapons.shoot(player, gun, tick, ready(player));
    }

    double spreadDegrees(UUID player, LaserGun gun) {
        return gun.spreadDegrees(weapons.burstShot(player));
    }

    int burstShot(UUID player) {
        return weapons.burstShot(player);
    }

    boolean completeReload(UUID player, LaserGun gun) {
        return ready(player) && weapons.completeReload(player, gun, tick);
    }

    boolean requestReload(UUID player, LaserGun gun) {
        return weapons.requestReload(player, gun, tick, ready(player));
    }

    boolean reloading(UUID player) {
        return weapons.reloading(player);
    }

    LaserGun reloadGun(UUID player) {
        return weapons.reloadGun(player);
    }

    LaserGun cancelReload(UUID player) {
        return weapons.cancelReload(player);
    }

    int reloadRemaining(UUID player) {
        return weapons.reloadRemaining(player, tick);
    }

    int ammo(UUID player, LaserGun gun) {
        return weapons.ammo(player, gun);
    }

    double health(UUID player) {
        return health.getOrDefault(player, MAX_HEALTH);
    }

    /** Applies one weapon hit while preserving the existing shield and score-transfer rules. */
    DamageResult damage(
            UUID victim,
            boolean victimOnRight,
            boolean bypassShield,
            UUID attacker,
            double amount) {
        return damage(victim, victimOnRight, bypassShield, attacker, amount, true);
    }

    DamageResult damage(
            UUID victim,
            boolean victimOnRight,
            boolean bypassShield,
            UUID attacker,
            double amount,
            boolean transferScore) {
        if (!ready(victim) || protectedFromHits(victim)) return DamageResult.IGNORED;
        int shield = shield(victim);
        if (!bypassShield && shield > 0) {
            if (shield == 1) shields.remove(victim);
            else shields.put(victim, shield - 1);
            return DamageResult.SHIELD;
        }
        double remaining = health(victim) - Math.max(0.0D, amount);
        if (remaining > 0.0D) {
            health.put(victim, remaining);
            return DamageResult.DAMAGED;
        }
        health.remove(victim);
        shields.remove(victim);
        respawns.put(victim, tick + RESPAWN_TICKS);
        if (transferScore)
            right = Math.clamp(right + (victimOnRight ? -transfer() : transfer()), 0, TOTAL_SCORE);
        if (attacker != null && !attacker.equals(victim)) kills.merge(attacker, 1, Integer::sum);
        return DamageResult.ELIMINATED;
    }

    void shield(UUID player, int hits) {
        if (ready(player)) shields.put(player, hits);
    }

    int shield(UUID player) {
        return shields.getOrDefault(player, 0);
    }

    void protect(UUID player) {
        protection.put(player, tick + 80);
    }

    boolean protectedFromHits(UUID player) {
        return protection.getOrDefault(player, 0) > tick;
    }

    int protectionRemaining(UUID player) {
        return Math.max(0, protection.getOrDefault(player, 0) - tick);
    }

    /**
     * Smoke is cover only. Environmental eliminations can bypass shields, but never spawn
     * protection.
     */
    boolean hit(UUID victim, boolean victimOnRight, boolean bypassShield, UUID attacker) {
        return damage(victim, victimOnRight, bypassShield, attacker, MAX_HEALTH)
                == DamageResult.ELIMINATED;
    }

    int kills(UUID player) {
        return kills.getOrDefault(player, 0);
    }

    void destroyShield(UUID player) {
        shields.remove(player);
    }

    int respawnRemaining(UUID player) {
        return Math.max(0, respawns.getOrDefault(player, tick) - tick);
    }

    boolean respawning(UUID player) {
        return respawns.containsKey(player);
    }

    boolean respawn(UUID player) {
        if (finished || !respawning(player) || respawnRemaining(player) > 0) return false;
        respawns.remove(player);
        weapons.forget(player);
        health.remove(player);
        protect(player);
        return true;
    }

    void disconnect(UUID player) {
        shields.remove(player);
        weapons.forget(player);
        health.remove(player);
        protection.remove(player);
        respawns.put(player, tick + RESPAWN_TICKS);
    }

    void forfeit(boolean rightLost) {
        right = rightLost ? 0 : TOTAL_SCORE;
    }
}
