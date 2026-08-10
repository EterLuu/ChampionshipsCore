package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** FIFO queue whose indivisible unit is a Party or a solo player. */
final class DailyQueue {
    private final GameTypeEnum game;
    private final LinkedHashMap<UUID, Group> groups = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, UUID> groupByPlayer = new LinkedHashMap<>();
    private int countdown = -1;
    private long revision;

    DailyQueue(GameTypeEnum game) { this.game = game; }
    GameTypeEnum game() { return game; }

    synchronized boolean canAdd(Set<UUID> players, DailyRules rules) {
        if (players.isEmpty() || players.size() > rules.teamSize()) return false;
        return players.stream().noneMatch(groupByPlayer::containsKey);
    }

    synchronized boolean add(@NotNull UUID groupId, @NotNull Set<UUID> players, @NotNull DailyRules rules) {
        if (!canAdd(players, rules) || groups.containsKey(groupId)) return false;
        Group group = new Group(groupId, new LinkedHashSet<>(players));
        groups.put(groupId, group);
        for (UUID player : players) groupByPlayer.put(player, groupId);
        countdown = -1;
        revision++;
        return true;
    }

    synchronized Set<UUID> removePlayer(UUID player) {
        UUID groupId = groupByPlayer.get(player);
        return groupId == null ? Set.of() : removeGroup(groupId);
    }

    synchronized Set<UUID> removeGroup(UUID groupId) {
        Group removed = groups.remove(groupId);
        if (removed == null) return Set.of();
        for (UUID member : removed.players()) groupByPlayer.remove(member);
        countdown = -1;
        revision++;
        return removed.players();
    }

    synchronized List<Group> take(int maximumPlayers) {
        List<Group> selected = new ArrayList<>();
        int count = 0;
        for (Group group : new ArrayList<>(groups.values())) {
            if (count + group.players().size() > maximumPlayers) continue;
            selected.add(group);
            count += group.players().size();
        }
        for (Group group : selected) removeGroup(group.id());
        countdown = -1;
        return selected;
    }

    synchronized void restore(List<Group> restored, DailyRules rules) {
        for (Group group : restored) add(group.id(), group.players(), rules);
        countdown = -1;
    }

    synchronized int size() { return groupByPlayer.size(); }
    synchronized int groupCount() { return groups.size(); }
    synchronized Set<UUID> players() { return Set.copyOf(groupByPlayer.keySet()); }
    synchronized int countdown() { return countdown; }
    synchronized void countdown(int value) { countdown = value; }
    synchronized long revision() { return revision; }

    record Group(UUID id, LinkedHashSet<UUID> players) {
        Group { players = new LinkedHashSet<>(players); }
        @Override public LinkedHashSet<UUID> players() { return new LinkedHashSet<>(players); }
    }
}
