package ink.ziip.championshipscore.api.game.laserbox.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Per-match allocation over the fixed LaserBox-world teaching sub-arenas. */
final class LaserBoxIntroductionRun implements AutoCloseable {
    private final ChampionshipsCore plugin;
    private final LaserBoxArea area;
    private final LaserBoxIntroductionArena arena;
    private final Map<UUID, Integer> laneByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Integer> connections = new HashMap<>();
    private boolean closed;

    LaserBoxIntroductionRun(
            ChampionshipsCore plugin, LaserBoxArea area, LaserBoxIntroductionArena arena) {
        this.plugin = plugin;
        this.area = area;
        this.arena = arena;
        int rightIndex = 0;
        if (area.getRightChampionshipTeam() != null)
            for (UUID uuid : area.getRightChampionshipTeam().getMembers())
                laneByPlayer.put(uuid, rightIndex++ % arena.laneCount());
        int leftIndex = 0;
        if (area.getLeftChampionshipTeam() != null)
            for (UUID uuid : area.getLeftChampionshipTeam().getMembers())
                laneByPlayer.put(uuid, leftIndex++ % arena.laneCount());
        for (UUID uuid : area.getParticipantUniqueIds())
            laneByPlayer.putIfAbsent(uuid, laneByPlayer.size() % arena.laneCount());
    }

    boolean current() {
        return !closed
                && plugin.isLoaded()
                && arena.ownedBy(area)
                && area.isIntroductionPhase();
    }

    boolean admitted(Player player) {
        return current()
                && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && laneByPlayer.containsKey(player.getUniqueId())
                && area.isIntroductionRosterParticipant(player);
    }

    ArrayList<Player> participants() {
        ArrayList<Player> result = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(laneByPlayer.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !admitted(player)) {
                connections.remove(uuid);
                continue;
            }
            result.add(player);
        }
        return result;
    }

    boolean needsSpawn(Player player) {
        return !Integer.valueOf(player.getEntityId()).equals(connections.get(player.getUniqueId()));
    }

    void markSpawned(Player player) {
        connections.put(player.getUniqueId(), player.getEntityId());
    }

    Location respawn(UUID uuid) {
        Integer lane = laneByPlayer.get(uuid);
        if (lane == null) return arena.spectatorSpawn();
        return arena.leastPopulatedSpawn(lane, area, laneByPlayer.keySet(), uuid);
    }

    boolean contains(Player player) {
        Integer lane = laneByPlayer.get(player.getUniqueId());
        return lane != null && arena.contains(lane, player.getLocation());
    }

    void disconnected(UUID uuid) {
        connections.remove(uuid);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        connections.clear();
        laneByPlayer.clear();
        arena.release(area);
    }
}
