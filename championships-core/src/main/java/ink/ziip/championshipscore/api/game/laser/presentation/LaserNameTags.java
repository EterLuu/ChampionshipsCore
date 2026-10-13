package ink.ziip.championshipscore.api.game.laser.presentation;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Match-owned name-tag suppression. Existing teams retain their members and presentation; only
 * NAME_TAG_VISIBILITY changes. Unassigned entries use a temporary, otherwise ordinary team. Board
 * leases are shared so releasing one match cannot restore another match's active tags.
 */
public final class LaserNameTags implements AutoCloseable {
    private static final Map<Scoreboard, BoardState> BOARDS = new IdentityHashMap<>();
    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Scoreboard> viewerBoards = new HashMap<>();
    private final Set<Scoreboard> heldBoards =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<UUID> roster = Set.of();
    private boolean closed;

    public LaserNameTags(Plugin plugin) {
        this.plugin = plugin;
        scheduler = new PlatformScheduler(plugin);
    }

    /** Resolves each name and scoreboard on that viewer's current entity thread. */
    public synchronized void refresh(Collection<UUID> participants) {
        if (closed) return;
        roster = Set.copyOf(participants);
        names.keySet().retainAll(roster);
        viewerBoards.keySet().retainAll(roster);
        reconcileBoards();
        for (UUID id : roster) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                remove(id);
                continue;
            }
            Runnable capture = () -> capture(player, id);
            if (Bukkit.isOwnedByCurrentRegion(player)) capture.run();
            else if (!scheduler.runEntity(player, capture, () -> retired(player, id)))
                retired(player, id);
        }
    }

    private synchronized void capture(Player player, UUID id) {
        if (closed
                || !plugin.isEnabled()
                || !roster.contains(id)
                || !player.isOnline()
                || Bukkit.getPlayer(id) != player) return;
        names.put(id, player.getName());
        Scoreboard board = player.getScoreboard();
        viewerBoards.put(id, board);
        reconcileBoards(board);
    }

    private synchronized void retired(Player player, UUID id) {
        Player current = Bukkit.getPlayer(id);
        if (current == null || current == player) remove(id);
    }

    public synchronized void remove(UUID id) {
        names.remove(id);
        viewerBoards.remove(id);
        reconcileBoards();
    }

    private void reconcileBoards() {
        reconcileBoards(null);
    }

    private void reconcileBoards(Scoreboard capturedBoard) {
        Set<Scoreboard> wanted = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        if (!closed) wanted.addAll(viewerBoards.values());
        synchronized (BOARDS) {
            for (Scoreboard board : java.util.List.copyOf(heldBoards)) {
                if (wanted.contains(board)) continue;
                BoardState state = BOARDS.get(board);
                if (state != null) {
                    state.release(this);
                    if (state.empty()) BOARDS.remove(board);
                }
                heldBoards.remove(board);
            }
            Set<String> entries = Set.copyOf(names.values());
            for (Scoreboard board : wanted) {
                if (capturedBoard != null && board != capturedBoard) continue;
                BOARDS.computeIfAbsent(board, BoardState::new).update(this, entries);
                heldBoards.add(board);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        names.clear();
        viewerBoards.clear();
        roster = Set.of();
        reconcileBoards();
    }

    /** Pure scoreboard ownership; no Player references or gameplay/spectator identity. */
    static final class BoardState {
        private record Lease(Team team, Team.OptionStatus original) {}

        private final Scoreboard board;
        private final Map<Object, Set<String>> scopes = new IdentityHashMap<>();
        private final Map<String, Lease> leases = new LinkedHashMap<>();
        private Team unassigned;
        private Set<String> temporaryEntries = Set.of();

        BoardState(Scoreboard board) {
            this.board = board;
        }

        void update(Object owner, Set<String> entries) {
            scopes.put(owner, Set.copyOf(entries));
            apply();
        }

        void release(Object owner) {
            scopes.remove(owner);
            apply();
        }

        boolean empty() {
            return scopes.isEmpty();
        }

        private void apply() {
            Set<String> entries = new HashSet<>();
            scopes.values().forEach(entries::addAll);
            Map<String, Team> wanted = new LinkedHashMap<>();
            Set<String> fallback = new HashSet<>();
            for (String entry : entries) {
                Team team = board.getEntryTeam(entry);
                if (team == null || team.equals(unassigned)) fallback.add(entry);
                else wanted.put(team.getName(), team);
            }
            for (var iterator = leases.entrySet().iterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                if (!registered(entry.getValue().team())
                        || wanted.containsKey(entry.getKey())
                                && !entry.getValue().team().equals(wanted.get(entry.getKey()))) {
                    iterator.remove();
                } else if (!wanted.containsKey(entry.getKey())) {
                    restore(entry.getValue());
                    iterator.remove();
                }
            }
            for (var entry : wanted.entrySet()) {
                Team team = entry.getValue();
                Team.OptionStatus current = team.getOption(Team.Option.NAME_TAG_VISIBILITY);
                if (!leases.containsKey(entry.getKey()) || current != Team.OptionStatus.NEVER)
                    leases.put(entry.getKey(), new Lease(team, current));
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
            if (unassigned != null && !registered(unassigned)) {
                unassigned = null;
                temporaryEntries = Set.of();
            }
            if (!fallback.isEmpty() && unassigned == null) {
                String id =
                        "laser" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                unassigned = board.registerNewTeam(id);
                unassigned.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
            if (unassigned != null) {
                for (String previous : temporaryEntries)
                    if (!fallback.contains(previous)
                            && unassigned.equals(board.getEntryTeam(previous)))
                        unassigned.removeEntry(previous);
                for (String entry : fallback)
                    if (!unassigned.equals(board.getEntryTeam(entry))) unassigned.addEntry(entry);
                if (fallback.isEmpty()) {
                    unassigned.unregister();
                    unassigned = null;
                }
            }
            temporaryEntries = Set.copyOf(fallback);
        }

        private static boolean registered(Team team) {
            try {
                return team.getScoreboard() != null;
            } catch (IllegalStateException ignored) {
                return false;
            }
        }

        private static void restore(Lease lease) {
            if (lease.team().getOption(Team.Option.NAME_TAG_VISIBILITY) == Team.OptionStatus.NEVER)
                lease.team().setOption(Team.Option.NAME_TAG_VISIBILITY, lease.original());
        }
    }
}
