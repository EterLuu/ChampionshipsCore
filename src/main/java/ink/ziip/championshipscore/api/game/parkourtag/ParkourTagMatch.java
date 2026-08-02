package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.game.spatial.ReplicatedSpatialLayout;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One team-vs-team Parkour Tag match in a single stamped arena copy. A match runs two cages at once: in the
 * RIGHT cage the right team's chaser hunts the left team's escapees, and in the LEFT cage the left team's
 * chaser hunts the right team's escapees (so each team simultaneously chases and escapes). All geometry is
 * copy-0's config template shifted onto this copy via {@link ParkourTagLayout#delta(int)}. This class holds
 * the per-match geometry and live state (chasers, survive times); the {@link ParkourTagArea} coordinator
 * drives lifecycle, scoring, messaging and boss bars across all matches.
 */
@Getter
public class ParkourTagMatch {
    private final int copyIndex;
    private final ChampionshipTeam right;
    private final ChampionshipTeam left;
    private final ParkourTagGeometry geometry;

    @Setter
    private volatile UUID rightAreaChaser;
    @Setter
    private volatile UUID leftAreaChaser;
    @Setter
    private volatile int rightTeamSurviveTime = -1;
    @Setter
    private volatile int leftTeamSurviveTime = -1;
    private final Map<UUID, Integer> playerSurviveTimes = new ConcurrentHashMap<>();

    public ParkourTagMatch(int copyIndex, ChampionshipTeam right, ChampionshipTeam left, ParkourTagConfig config) {
        this(copyIndex, right, left, new ReplicatedSpatialLayout<>(ParkourTagGeometry.from(config),
                config.getCopyGrid(), config.getCopyCount()).geometry(copyIndex));
    }

    public ParkourTagMatch(int copyIndex, ChampionshipTeam right, ChampionshipTeam left,
                           ParkourTagGeometry geometry) {
        this.copyIndex = copyIndex;
        this.right = right;
        this.left = left;
        this.geometry = geometry;
    }

    public Location getRightPreSpawn() { return geometry.getRightPreSpawn(); }
    public Location getLeftPreSpawn() { return geometry.getLeftPreSpawn(); }
    public Location getSpectatorSpawn() { return geometry.getSpectatorSpawn(); }
    public Location getLeftAreaChaserSpawn() { return geometry.getLeftZone().getChaserSpawn(); }
    public List<Location> getLeftAreaEscapeeSpawns() { return geometry.getLeftZone().getEscapeeSpawns(); }
    public Location getRightAreaChaserSpawn() { return geometry.getRightZone().getChaserSpawn(); }
    public List<Location> getRightAreaEscapeeSpawns() { return geometry.getRightZone().getEscapeeSpawns(); }

    public boolean contains(Player player) {
        UUID uuid = player.getUniqueId();
        return right.getMembers().contains(uuid) || left.getMembers().contains(uuid);
    }

    public boolean isInLeftArea(Location location) {
        return geometry.getLeftZone().contains(location);
    }

    public boolean isInRightArea(Location location) {
        return geometry.getRightZone().contains(location);
    }

    public boolean isInArea(Location location) {
        return isInLeftArea(location) || isInRightArea(location);
    }

    public BoundingBox getLeftAreaBox() {
        return geometry.getLeftZone().box();
    }

    public BoundingBox getRightAreaBox() {
        return geometry.getRightZone().box();
    }

    /** Right team's escapees (everyone on the right team except its chaser). */
    public List<UUID> getRightTeamEscapees() {
        List<UUID> escapees = new ArrayList<>();
        for (UUID uuid : right.getMembers()) {
            if (!uuid.equals(rightAreaChaser)) escapees.add(uuid);
        }
        return escapees;
    }

    /** Left team's escapees (everyone on the left team except its chaser). */
    public List<UUID> getLeftTeamEscapees() {
        List<UUID> escapees = new ArrayList<>();
        for (UUID uuid : left.getMembers()) {
            if (!uuid.equals(leftAreaChaser)) escapees.add(uuid);
        }
        return escapees;
    }

    /** Players in the RIGHT cage's escapee role: the left team's online non-chasers. */
    public List<Player> getRightAreaEscapees() {
        List<Player> escapees = new ArrayList<>();
        for (Player player : left.getOnlinePlayers()) {
            if (!player.getUniqueId().equals(leftAreaChaser)) escapees.add(player);
        }
        return escapees;
    }

    /** Players in the LEFT cage's escapee role: the right team's online non-chasers. */
    public List<Player> getLeftAreaEscapees() {
        List<Player> escapees = new ArrayList<>();
        for (Player player : right.getOnlinePlayers()) {
            if (!player.getUniqueId().equals(rightAreaChaser)) escapees.add(player);
        }
        return escapees;
    }

    @Nullable
    public Player getRightAreaChaserPlayer() {
        return rightAreaChaser == null ? null : Bukkit.getPlayer(rightAreaChaser);
    }

    @Nullable
    public Player getLeftAreaChaserPlayer() {
        return leftAreaChaser == null ? null : Bukkit.getPlayer(leftAreaChaser);
    }

    public boolean isChaser(Player player) {
        UUID uuid = player.getUniqueId();
        return uuid.equals(rightAreaChaser) || uuid.equals(leftAreaChaser);
    }

    public boolean isEscapee(Player player) {
        return getRightAreaEscapees().contains(player) || getLeftAreaEscapees().contains(player);
    }

    @Nullable
    public UUID getAreaChaser(Location location) {
        if (isInLeftArea(location)) return leftAreaChaser;
        if (isInRightArea(location)) return rightAreaChaser;
        return null;
    }

    /** Total escapees in the cage that {@code location} is in (used by placeholders). */
    public int getAreaEscapeesNums(Location location) {
        if (isInLeftArea(location)) return right.getMembers().size() - 1;
        if (isInRightArea(location)) return left.getMembers().size() - 1;
        return 0;
    }

    /** Still-alive escapees in the cage that {@code location} is in (used by placeholders). */
    public int getAreaSurvivedEscapeesNums(Location location) {
        int i = 0;
        if (isInLeftArea(location)) {
            i = right.getMembers().size() - 1;
            for (UUID uuid : getRightTeamEscapees()) {
                if (playerSurviveTimes.containsKey(uuid)) i--;
            }
        } else if (isInRightArea(location)) {
            i = left.getMembers().size() - 1;
            for (UUID uuid : getLeftTeamEscapees()) {
                if (playerSurviveTimes.containsKey(uuid)) i--;
            }
        }
        return i;
    }

    /**
     * Recomputes whether either team has now been fully caught, stamping its survive time at {@code elapsed}
     * seconds. Returns the teams that became fully caught on THIS call (for the coordinator to announce).
     */
    public synchronized List<ChampionshipTeam> updateTeamSurviveTimes(int elapsed) {
        List<ChampionshipTeam> newlyCaught = new ArrayList<>();
        int rightSurvivor = right.getMembers().size() - 1;
        int leftSurvivor = left.getMembers().size() - 1;
        for (UUID uuid : playerSurviveTimes.keySet()) {
            if (getRightTeamEscapees().contains(uuid)) rightSurvivor--;
            if (getLeftTeamEscapees().contains(uuid)) leftSurvivor--;
        }
        if (rightSurvivor == 0 && rightTeamSurviveTime == -1) {
            rightTeamSurviveTime = elapsed;
            newlyCaught.add(right);
        }
        if (leftSurvivor == 0 && leftTeamSurviveTime == -1) {
            leftTeamSurviveTime = elapsed;
            newlyCaught.add(left);
        }
        return newlyCaught;
    }
}
