package ink.ziip.championshipscore.api.game.buildmart.library;

import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartOrderPool;

import java.util.Collections;
import java.util.List;

/**
 * The hub's normal-blueprint library for one area: the current set of selectable orders, re-rolled from
 * the {@link BuildMartOrderPool} at round start and on every refresh tick. Already-selected builds are
 * unaffected by a refresh — only this selectable list changes.
 */
public class BlueprintLibrary {
    /** How many orders are offered at once. */
    public static final int SIZE = 7;

    private final BuildMartOrderPool pool;
    private volatile List<BuildMartBlueprint> current = List.of();

    public BlueprintLibrary(BuildMartOrderPool pool) {
        this.pool = pool;
    }

    /** Re-rolls the selectable list from the pool. */
    public void refresh() {
        current = pool == null ? List.of() : List.copyOf(pool.drawNormal(SIZE));
    }

    /** The current selectable orders (unmodifiable snapshot). */
    public List<BuildMartBlueprint> current() {
        return Collections.unmodifiableList(current);
    }

    /** Looks up a currently-offered blueprint by id, or {@code null} if it isn't on the board. */
    public BuildMartBlueprint currentById(String id) {
        for (BuildMartBlueprint blueprint : current) {
            if (blueprint.getId().equals(id)) return blueprint;
        }
        return null;
    }
}
