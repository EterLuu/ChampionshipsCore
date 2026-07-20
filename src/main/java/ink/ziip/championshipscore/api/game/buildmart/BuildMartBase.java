package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of one team's base, parsed from a {@code bases.<teamId>} config sub-section. A base holds the
 * team spawn, the portal region that sends players to the hub, and the build plots. Each build plot is a
 * pair of anchors: a {@code reference} anchor where the ghost reference build is pasted, and a
 * {@code build} anchor where the player replicates it (the validation origin).
 *
 * <p>All fields are optional so a partially-configured base still loads; callers must null-check the
 * pieces they use. Phase 2 only relies on {@link #getSpawn()} and {@link #getPortalToHub()}.
 */
@Getter
public class BuildMartBase {
    private final int teamId;
    @Nullable
    private final Location spawn;
    /** Region a player walks into to be teleported to the hub. */
    @Nullable
    private final BoundingBox portalToHub;

    /** Build/reference anchors for the 3 normal plots (parallel lists, index = plot number). */
    private final List<Location> normalBuildAnchors = new ArrayList<>();
    private final List<Location> normalReferenceAnchors = new ArrayList<>();

    @Nullable
    private final Location goldenBuildAnchor;
    @Nullable
    private final Location goldenReferenceAnchor;

    public BuildMartBase(int teamId, ConfigurationSection section) {
        this.teamId = teamId;
        this.spawn = loc(section, "spawn");
        this.portalToHub = box(loc(section, "portal-pos1"), loc(section, "portal-pos2"));

        for (int i = 1; i <= 3; i++) {
            Location build = loc(section, "normal-plot-" + i);
            Location ref = loc(section, "normal-ref-" + i);
            if (build != null) normalBuildAnchors.add(build);
            if (ref != null) normalReferenceAnchors.add(ref);
        }

        this.goldenBuildAnchor = loc(section, "golden-plot");
        this.goldenReferenceAnchor = loc(section, "golden-ref");
    }

    /** Copy constructor that shifts every anchor of {@code template} by {@code delta} for a new seat. */
    private BuildMartBase(int teamId, @NotNull BuildMartBase template, @NotNull Vector delta) {
        this.teamId = teamId;
        this.spawn = shift(template.spawn, delta);
        this.portalToHub = shift(template.portalToHub, delta);
        for (Location anchor : template.normalBuildAnchors) {
            Location shifted = shift(anchor, delta);
            if (shifted != null) normalBuildAnchors.add(shifted);
        }
        for (Location anchor : template.normalReferenceAnchors) {
            Location shifted = shift(anchor, delta);
            if (shifted != null) normalReferenceAnchors.add(shifted);
        }
        this.goldenBuildAnchor = shift(template.goldenBuildAnchor, delta);
        this.goldenReferenceAnchor = shift(template.goldenReferenceAnchor, delta);
    }

    /**
     * Returns a copy of this base (treated as seat 0's template) with all anchors translated by
     * {@code delta} and re-keyed to {@code seat}. Used to derive every seat's geometry from one configured
     * template; see {@link BuildMartLayout}.
     */
    public BuildMartBase translated(int seat, @NotNull Vector delta) {
        return new BuildMartBase(seat, this, delta);
    }

    @Nullable
    private static Location shift(@Nullable Location location, @NotNull Vector delta) {
        return location == null ? null : location.clone().add(delta);
    }

    @Nullable
    private static BoundingBox shift(@Nullable BoundingBox box, @NotNull Vector delta) {
        return box == null ? null : box.clone().shift(delta);
    }

    @Nullable
    private static Location loc(ConfigurationSection section, String key) {
        String raw = section.getString(key);
        if (raw == null || raw.isBlank()) return null;
        return Utils.getLocation(raw);
    }

    @Nullable
    private static BoundingBox box(@Nullable Location a, @Nullable Location b) {
        if (a == null || b == null) return null;
        return BoundingBox.of(new Vector(a.getX(), a.getY(), a.getZ()), new Vector(b.getX(), b.getY(), b.getZ()));
    }
}
