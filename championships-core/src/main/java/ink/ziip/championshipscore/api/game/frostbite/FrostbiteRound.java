package ink.ziip.championshipscore.api.game.frostbite;

import java.util.*;

/** One round's authoritative roster, freeze attribution and kill ledger; server tick time only. */
public final class FrostbiteRound {
    public record Seat(int team, int arena) {}
    public record Freeze(UUID attacker, int until) {}
    private final Map<UUID, Seat> seats;
    private final Map<UUID, Freeze> frozen = new HashMap<>();
    private final Map<UUID, Integer> heat = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Set<UUID> departed = new HashSet<>();

    public FrostbiteRound(List<List<UUID>> teams, int round) {
        if (teams.size() < 2 || teams.size() > 16 || round < 0 || round > 3)
            throw new IllegalArgumentException("需要 2–16 支队伍，轮次须为 1–4");
        Map<UUID, Seat> result = new LinkedHashMap<>();
        for (int team = 0; team < teams.size(); team++) {
            var members = teams.get(team).stream().sorted().toList();
            if (members.isEmpty() || members.size() > 4)
                throw new IllegalArgumentException("每队需要 1–4 名参赛者");
            for (int member = 0; member < members.size(); member++) {
                UUID id = Objects.requireNonNull(members.get(member));
                // GF(4) yields every opposing pairing once over four rounds for four teams.
                int arena = member ^ multiply(team % 4, round);
                if (result.put(id, new Seat(team, arena)) != null)
                    throw new IllegalArgumentException("参赛名单包含重复玩家");
            }
        }
        seats = Collections.unmodifiableMap(result);
    }
    static int multiply(int a, int b) {
        int result = 0;
        for (; b != 0; b >>= 1) {
            if ((b & 1) != 0) result ^= a;
            a <<= 1;
            if ((a & 4) != 0) a ^= 7;
        }
        return result;
    }
    public Map<UUID, Seat> seats() { return seats; }
    public boolean active(UUID id) { return seats.containsKey(id) && !departed.contains(id); }
    public boolean enemies(UUID a, UUID b) {
        return active(a) && active(b) && seats.get(a).arena == seats.get(b).arena
                && seats.get(a).team != seats.get(b).team;
    }
    public boolean canAct(UUID id) { return active(id) && !frozen.containsKey(id); }
    public boolean heated(UUID id, int tick) { return heat.getOrDefault(id, 0) > tick; }
    public Freeze freezeState(UUID id) { return frozen.get(id); }
    public boolean freeze(UUID attacker, UUID victim, int tick, int duration) {
        if (!active(attacker) || !enemies(attacker, victim) || !canAct(victim) || heated(victim, tick)) return false;
        frozen.put(victim, new Freeze(attacker, tick + duration));
        return true;
    }
    public void selfFreeze(UUID id, int tick, int duration) {
        if (canAct(id)) frozen.put(id, new Freeze(null, tick + duration));
    }
    public boolean thaw(UUID id) { return frozen.remove(id) != null; }
    public void heat(UUID id, int until) { heat.put(id, until); }
    public List<UUID> expired(int tick) {
        return frozen.entrySet().stream().filter(e -> e.getValue().until <= tick).map(Map.Entry::getKey).toList();
    }
    /** Removes attribution before awarding, making duplicate expiry/death callbacks harmless. */
    public UUID die(UUID victim) {
        Freeze state = frozen.remove(victim);
        heat.remove(victim);
        UUID killer = state == null ? null : state.attacker;
        if (killer != null && seats.containsKey(killer) && !killer.equals(victim)) kills.merge(killer, 1, Integer::sum);
        return killer;
    }
    public UUID instantKill(UUID attacker, UUID victim, int tick) {
        if (!canAct(attacker) || !enemies(attacker, victim) || heated(victim, tick)) return null;
        frozen.put(victim, new Freeze(attacker, tick));
        return die(victim);
    }
    public UUID leave(UUID id) {
        UUID killer = die(id);
        departed.add(id);
        return killer;
    }
    public int kills(UUID id) { return kills.getOrDefault(id, 0); }
    public Map<UUID, Integer> kills() { return Map.copyOf(kills); }
}
