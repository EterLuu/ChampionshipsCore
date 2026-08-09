package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.game.spatial.SpatialTransform;
import ink.ziip.championshipscore.api.game.spatial.SpatialTemplate;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of one team's base, derived from the configured {@code base} template. A base holds the team
 * portal landing point and the build plots. Each build plot has a
 * reference anchor, a player build anchor, and a submission button.
 *
 * <p>All fields are optional so an unfinished setup can still load. A game starts only after the template
 * is complete.
 */
@Getter
public class BuildMartBase implements SpatialTemplate<BuildMartBase> {
    private final int teamId;
    @Nullable
    private final Location portalPoint;

    /** Build/reference anchors for the 3 normal plots (parallel lists, index = plot number). */
    private final List<Location> normalBuildAnchors = new ArrayList<>();
    private final List<Location> normalReferenceAnchors = new ArrayList<>();

    /** Per-plot normal submit-button block coords (null-padded so index = plot number, 0-based). */
    private final List<Location> normalSubmitAnchors = new ArrayList<>();

    @Nullable
    private final Location goldenBuildAnchor;
    /** The golden plot's submit-button block coord. */
    @Nullable
    private final Location goldenSubmitAnchor;

    public BuildMartBase(int teamId, ConfigurationSection section) {
        this.teamId = teamId;
        this.portalPoint = loc(section, "portal");

        for (int i = 1; i <= 3; i++) {
            Location build = loc(section, "normal-plot-" + i);
            Location ref = loc(section, "normal-ref-" + i);
            if (build != null) normalBuildAnchors.add(build);
            if (ref != null) normalReferenceAnchors.add(ref);
            // Always add (null-padded) so the list index lines up with the plot number.
            normalSubmitAnchors.add(loc(section, "normal-submit-" + i));
        }

        this.goldenBuildAnchor = loc(section, "golden-plot");
        this.goldenSubmitAnchor = loc(section, "golden-submit");
    }

    /** Copy constructor that shifts every anchor of {@code template} by {@code delta} for a new seat. */
    private BuildMartBase(int teamId, @NotNull BuildMartBase template, @NotNull SpatialTransform transform) {
        this.teamId = teamId;
        this.portalPoint = transform.apply(template.portalPoint);
        for (Location anchor : template.normalBuildAnchors) {
            Location shifted = transform.apply(anchor);
            if (shifted != null) normalBuildAnchors.add(shifted);
        }
        for (Location anchor : template.normalReferenceAnchors) {
            Location shifted = transform.apply(anchor);
            if (shifted != null) normalReferenceAnchors.add(shifted);
        }
        for (Location anchor : template.normalSubmitAnchors) {
            normalSubmitAnchors.add(transform.apply(anchor)); // keep null-padded alignment
        }
        this.goldenBuildAnchor = transform.apply(template.goldenBuildAnchor);
        this.goldenSubmitAnchor = transform.apply(template.goldenSubmitAnchor);
    }

    /**
     * Returns a copy of this base (treated as seat 0's template) with all anchors translated by
     * {@code delta} and re-keyed to {@code seat}. Used to derive every seat's geometry from one configured
     * template; see {@link BuildMartLayout}.
     */
    @Override
    public @NotNull BuildMartBase transform(@NotNull SpatialTransform transform) {
        return new BuildMartBase(teamId, this, transform);
    }

    /** Re-keys already transformed geometry to the participating team's seat. */
    public @NotNull BuildMartBase forSeat(int seat) {
        return new BuildMartBase(seat, this, SpatialTransform.IDENTITY);
    }

    /** All geometry required by the live Build Mart rules has been captured for this base template. */
    public boolean isComplete() {
        return portalPoint != null
                && normalBuildAnchors.size() == 3 && normalReferenceAnchors.size() == 3
                && normalSubmitAnchors.size() == 3 && normalSubmitAnchors.stream().allMatch(java.util.Objects::nonNull)
                && goldenBuildAnchor != null && goldenSubmitAnchor != null;
    }

    @Nullable
    private static Location loc(ConfigurationSection section, String key) {
        String raw = section.getString(key);
        if (raw == null || raw.isBlank()) return null;
        return Utils.getLocation(raw);
    }

}
