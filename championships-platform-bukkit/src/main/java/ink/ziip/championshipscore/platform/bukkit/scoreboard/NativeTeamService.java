package ink.ziip.championshipscore.platform.bukkit.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Shared projection of Championships teams onto Minecraft's main scoreboard.
 *
 * <p>Folia does not support allocating and assigning per-player scoreboards, and native team
 * mutation is platform-dependent. Keeping the optional projection here gives Core and remote
 * workers the same colour and membership semantics where supported, without sharing either
 * plugin's business-level team manager.</p>
 */
public final class NativeTeamService {
    private final Scoreboard scoreboard;
    /** Optional projections can be disabled permanently when a platform rejects team mutation. */
    private volatile boolean mutationSupported = true;

    public NativeTeamService(Scoreboard scoreboard) {
        this.scoreboard = Objects.requireNonNull(scoreboard, "scoreboard");
    }

    public static NativeTeamService mainScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) throw new IllegalStateException("The main scoreboard is not available");
        return new NativeTeamService(manager.getMainScoreboard());
    }

    public Scoreboard scoreboard() {
        return scoreboard;
    }

    public boolean mutationSupported() {
        return mutationSupported;
    }

    public void markMutationUnsupported() {
        mutationSupported = false;
    }

    public NativeTeamOverlay newOverlay() {
        return new NativeTeamOverlay(this);
    }

    /** Replaces a team with one exact, fully configured projection. */
    public Team replaceTeam(String scoreboardId, String displayName, String colorName,
                            String colorCode, Collection<String> entries,
                            Team.OptionStatus collisionRule) {
        validateScoreboardId(scoreboardId);
        Team previous = scoreboard.getTeam(scoreboardId);
        unregister(previous);
        Team team = scoreboard.registerNewTeam(scoreboardId);
        try {
            configure(team, displayName, colorName, colorCode, collisionRule);
            syncEntries(team, entries);
            return team;
        } catch (RuntimeException failure) {
            unregister(team);
            throw failure;
        }
    }

    /** Creates a team when absent, then applies the same metadata used by replacement. */
    public Team getOrCreateTeam(String scoreboardId, String displayName, String colorName,
                                String colorCode, Team.OptionStatus collisionRule) {
        validateScoreboardId(scoreboardId);
        Team team = scoreboard.getTeam(scoreboardId);
        if (team == null) team = scoreboard.registerNewTeam(scoreboardId);
        configure(team, displayName, colorName, colorCode, collisionRule);
        return team;
    }

    public void configure(Team team, String displayName, String colorName, String colorCode,
                          Team.OptionStatus collisionRule) {
        Objects.requireNonNull(team, "team");
        NamedTextColor color = resolveNamedColor(colorName, colorCode);
        team.displayName(Component.text(Objects.requireNonNull(displayName, "displayName"), color));
        team.color(color);
        team.setOption(Team.Option.COLLISION_RULE,
                Objects.requireNonNull(collisionRule, "collisionRule"));
    }

    /** Makes the native entry set exactly match the requested usernames. */
    public void syncEntries(Team team, Collection<String> requestedEntries) {
        Objects.requireNonNull(team, "team");
        Set<String> entries = normalizedEntries(requestedEntries);
        for (String existing : Set.copyOf(team.getEntries())) {
            if (!entries.contains(existing)) team.removeEntry(existing);
        }
        for (String entry : entries) {
            if (!team.hasEntry(entry)) team.addEntry(entry);
        }
    }

    public void unregister(Team team) {
        if (team == null) return;
        try {
            team.unregister();
        } catch (IllegalStateException ignored) {
        }
    }

    public static NamedTextColor resolveNamedColor(String colorName, String colorCode) {
        TextColor parsed = colorCode == null ? null : TextColor.fromHexString(colorCode);
        if (parsed != null) {
            NamedTextColor exact = NamedTextColor.namedColor(parsed.value());
            if (exact != null) return exact;
        }
        return resolveNamedColor(colorName);
    }

    public static NamedTextColor resolveNamedColor(String colorName) {
        String normalized = Objects.requireNonNull(colorName, "colorName").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "green" -> NamedTextColor.DARK_GREEN;
            case "brown" -> NamedTextColor.DARK_RED;
            case "lime" -> NamedTextColor.DARK_AQUA;
            case "pink", "magenta" -> NamedTextColor.LIGHT_PURPLE;
            case "light_blue" -> NamedTextColor.AQUA;
            case "cyan" -> NamedTextColor.GREEN;
            case "purple" -> NamedTextColor.DARK_PURPLE;
            case "orange" -> NamedTextColor.GOLD;
            case "black", "gray" -> NamedTextColor.DARK_GRAY;
            case "light_gray" -> NamedTextColor.GRAY;
            default -> {
                NamedTextColor named = NamedTextColor.NAMES.value(normalized);
                yield named == null ? NamedTextColor.WHITE : named;
            }
        };
    }

    static Set<String> normalizedEntries(Collection<String> requestedEntries) {
        Objects.requireNonNull(requestedEntries, "requestedEntries");
        Set<String> result = new LinkedHashSet<>();
        for (String entry : requestedEntries) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException("Native team entries must not be blank");
            }
            result.add(entry);
        }
        return Set.copyOf(result);
    }

    static void validateScoreboardId(String scoreboardId) {
        if (scoreboardId == null || !scoreboardId.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Invalid native team id: " + scoreboardId);
        }
    }
}
