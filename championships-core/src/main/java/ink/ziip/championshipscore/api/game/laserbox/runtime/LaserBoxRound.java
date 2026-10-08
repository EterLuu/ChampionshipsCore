package ink.ziip.championshipscore.api.game.laserbox.runtime;

import ink.ziip.championshipscore.api.game.laserbox.model.LaserBoxGun;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure match state. Tick deadlines prevent cooldown/respawn races across disconnects. */
final class LaserBoxRound {
    private static final int RESPAWN_TICKS = 80;
    private static final int BURST_RESET_QUARTER_TICKS = 32;
    static final int INITIAL_SCORE = 50;
    static final int INITIAL_TRANSFER = 5;
    private static final int TOTAL_SCORE = INITIAL_SCORE * 2;
    private int right = INITIAL_SCORE;
    private int tick;
    private boolean finished;
    private final Map<UUID, Integer> shots = new HashMap<>();
    private final Map<UUID, Integer> lastFiredAt = new HashMap<>();
    private final Map<UUID, Integer> burstShots = new HashMap<>();
    private final Map<UUID, Integer> ammunition = new HashMap<>();
    private final Map<UUID, Reload> reloads = new HashMap<>();
    private final Map<UUID, Double> health = new HashMap<>();
    private final Map<UUID, Integer> respawns = new HashMap<>();
    private final Map<UUID, Integer> shields = new HashMap<>();
    private final Map<UUID, Integer> protection = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

    enum FireResult {
        FIRED,
        COOLDOWN,
        RELOADING,
        NOT_READY
    }

    enum DamageResult {
        IGNORED,
        SHIELD,
        DAMAGED,
        ELIMINATED
    }

    private static final double MAX_HEALTH = 100.0D;

    private record Reload(int deadline, LaserBoxGun gun) {}

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
        return true;
    }

    boolean ready(UUID player) {
        return !finished && !respawns.containsKey(player);
    }

    /** Compatibility path for the old single laser tests and integrations. */
    boolean shoot(UUID player) {
        // The original LaserBox laser fired every 6.25 ticks.  Keep this overload stable for
        // callers that do not participate in the weapon-selection system.
        int now = tick * 4;
        Integer deadline = shots.get(player);
        if (!ready(player) || deadline != null && deadline > now) return false;
        int base = deadline != null && now - deadline < 4 ? deadline : now;
        shots.put(player, base + 25);
        return true;
    }

    FireResult shoot(UUID player, LaserBoxGun gun) {
        if (!ready(player) || gun == null) return FireResult.NOT_READY;
        completeReload(player, gun);
        if (reloads.containsKey(player)) return FireResult.RELOADING;

        int now = tick * 4;
        Integer deadline = shots.get(player);
        if (deadline != null && deadline > now) return FireResult.COOLDOWN;

        int ammo = ammunition.getOrDefault(player, gun.magazine());
        if (ammo <= 0) {
            reloads.put(player, new Reload(tick + gun.reloadTicks(), gun));
            return FireResult.RELOADING;
        }

        int base =
                deadline != null && now - deadline < gun.fireIntervalQuarterTicks() / 2
                        ? deadline
                        : now;
        shots.put(player, base + gun.fireIntervalQuarterTicks());
        ammunition.put(player, ammo - 1);
        int burstShot = 0;
        Integer previous = lastFiredAt.get(player);
        if (previous != null && now - previous < BURST_RESET_QUARTER_TICKS)
            burstShot = burstShots.getOrDefault(player, 0) + 1;
        burstShots.put(player, burstShot);
        lastFiredAt.put(player, now);
        if (ammo == 1) reloads.put(player, new Reload(tick + gun.reloadTicks(), gun));
        return FireResult.FIRED;
    }

    double spreadDegrees(UUID player, LaserBoxGun gun) {
        return gun.spreadDegrees(burstShots.getOrDefault(player, 0));
    }

    int burstShot(UUID player) {
        return burstShots.getOrDefault(player, 0);
    }

    boolean completeReload(UUID player, LaserBoxGun gun) {
        Reload reload = reloads.get(player);
        if (reload == null || reload.gun() != gun || reload.deadline() > tick) return false;
        reloads.remove(player);
        ammunition.put(player, gun.magazine());
        return true;
    }

    /** Starts a player-requested reload when the current magazine is not already full. */
    boolean requestReload(UUID player, LaserBoxGun gun) {
        if (!ready(player) || gun == null || reloads.containsKey(player)) return false;
        int ammo = ammunition.getOrDefault(player, gun.magazine());
        if (ammo >= gun.magazine()) return false;
        reloads.put(player, new Reload(tick + gun.reloadTicks(), gun));
        return true;
    }

    boolean reloading(UUID player) {
        return reloads.containsKey(player);
    }

    LaserBoxGun reloadGun(UUID player) {
        Reload reload = reloads.get(player);
        return reload == null ? null : reload.gun();
    }

    LaserBoxGun cancelReload(UUID player) {
        Reload reload = reloads.remove(player);
        return reload == null ? null : reload.gun();
    }

    int reloadRemaining(UUID player) {
        Reload reload = reloads.get(player);
        return Math.max(0, reload == null ? 0 : reload.deadline() - tick);
    }

    int ammo(UUID player, LaserBoxGun gun) {
        return ammunition.getOrDefault(player, gun.magazine());
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
        shots.remove(player);
        lastFiredAt.remove(player);
        burstShots.remove(player);
        reloads.remove(player);
        ammunition.remove(player);
        health.remove(player);
        protect(player);
        return true;
    }

    void disconnect(UUID player) {
        shields.remove(player);
        shots.remove(player);
        lastFiredAt.remove(player);
        burstShots.remove(player);
        ammunition.remove(player);
        reloads.remove(player);
        health.remove(player);
        protection.remove(player);
        respawns.put(player, tick + RESPAWN_TICKS);
    }

    void forfeit(boolean rightLost) {
        right = rightLost ? 0 : TOTAL_SCORE;
    }
}
