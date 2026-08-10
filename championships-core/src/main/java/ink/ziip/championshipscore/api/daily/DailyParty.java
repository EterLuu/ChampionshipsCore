package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** In-memory social group. One selected game and one queue state are shared by every member. */
public final class DailyParty {
    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private GameTypeEnum selectedGame;
    private long revision;

    DailyParty(@NotNull UUID creator) {
        leader = creator;
        members.add(creator);
    }

    public UUID id() { return id; }
    public synchronized UUID leader() { return leader; }
    public synchronized boolean isLeader(UUID player) { return leader.equals(player); }
    public synchronized Set<UUID> members() { return Set.copyOf(members); }
    public synchronized int size() { return members.size(); }
    public synchronized @Nullable GameTypeEnum selectedGame() { return selectedGame; }
    public synchronized long revision() { return revision; }

    synchronized boolean add(UUID player) {
        boolean changed = members.add(player);
        if (changed) revision++;
        return changed;
    }

    synchronized boolean remove(UUID player) {
        boolean changed = members.remove(player);
        if (!changed) return false;
        if (leader.equals(player) && !members.isEmpty()) leader = members.iterator().next();
        revision++;
        return true;
    }

    synchronized void transferLeadership(UUID player) {
        if (members.contains(player) && !leader.equals(player)) {
            leader = player;
            revision++;
        }
    }

    /** Any member may change this value; callers serialize the accompanying queue migration. */
    synchronized long select(@NotNull GameTypeEnum game) {
        selectedGame = game;
        return ++revision;
    }
}
