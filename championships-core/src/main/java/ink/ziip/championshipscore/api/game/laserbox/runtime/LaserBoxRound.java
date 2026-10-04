package ink.ziip.championshipscore.api.game.laserbox.runtime;

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
    private final Map<UUID, Integer> shots = new HashMap<>();
    private final Map<UUID, Integer> respawns = new HashMap<>();
    private final Map<UUID, Integer> shields = new HashMap<>();
    private final Map<UUID, Integer> protection = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

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

    boolean shoot(UUID player) {
        // Quarter-tick deadlines give a 6.25-tick interval (3.2 shots/s), 20% below 4/s.
        int now = tick * 4;
        Integer deadline = shots.get(player);
        if (!ready(player) || deadline != null && deadline > now) return false;
        int base = deadline != null && now - deadline < 4 ? deadline : now;
        shots.put(player, base + 25);
        return true;
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
        if (!ready(victim) || protectedFromHits(victim)) return false;
        int shield = shield(victim);
        if (!bypassShield && shield > 0) {
            if (shield == 1) shields.remove(victim);
            else shields.put(victim, shield - 1);
            return false;
        }
        shields.remove(victim);
        respawns.put(victim, tick + RESPAWN_TICKS);
        right = Math.clamp(right + (victimOnRight ? -transfer() : transfer()), 0, TOTAL_SCORE);
        if (attacker != null && !attacker.equals(victim)) kills.merge(attacker, 1, Integer::sum);
        return true;
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
        protect(player);
        return true;
    }

    void disconnect(UUID player) {
        shields.remove(player);
        shots.remove(player);
        protection.remove(player);
        respawns.put(player, tick + RESPAWN_TICKS);
    }

    void forfeit(boolean rightLost) {
        right = rightLost ? 0 : TOTAL_SCORE;
    }
}
