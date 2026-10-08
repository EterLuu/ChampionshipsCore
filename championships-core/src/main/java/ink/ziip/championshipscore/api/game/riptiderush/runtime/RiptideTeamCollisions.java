package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Disables client-predicted pushing without replacing players' native team identities. */
final class RiptideTeamCollisions implements AutoCloseable {
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final Map<Team, OriginalRule> originalRules = new HashMap<>();

    void disable(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        Team team = scoreboard.getEntryTeam(player.getName());
        Team previous = playerTeams.remove(player.getUniqueId());
        if (!Objects.equals(previous, team)) restoreIfUnused(previous);
        if (team == null) return;

        playerTeams.put(player.getUniqueId(), team);
        Team.OptionStatus rule = team.getOption(Team.Option.COLLISION_RULE);
        originalRules.putIfAbsent(team, new OriginalRule(scoreboard, team.getName(), rule));
        if (rule != Team.OptionStatus.NEVER)
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    void restore(UUID playerId) {
        restoreIfUnused(playerTeams.remove(playerId));
    }

    private void restoreIfUnused(Team team) {
        if (team == null || playerTeams.containsValue(team)) return;
        OriginalRule original = originalRules.remove(team);
        if (original == null) return;
        // An old arena must not mutate a replacement team or a later collision policy.
        if (team.equals(original.scoreboard().getTeam(original.name()))
                && team.getOption(Team.Option.COLLISION_RULE) == Team.OptionStatus.NEVER)
            team.setOption(Team.Option.COLLISION_RULE, original.rule());
    }

    @Override
    public void close() {
        playerTeams.clear();
        for (Team team : new ArrayList<>(originalRules.keySet())) restoreIfUnused(team);
    }

    private record OriginalRule(Scoreboard scoreboard, String name, Team.OptionStatus rule) {}
}
