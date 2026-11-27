package ink.ziip.championshipscore.api.game.craftingfeast.model;

import java.util.List;
import java.util.Set;

/** A named visitor with a themed menu. Villagers use the map-wide VIP bonus. */
public record FeastGuestProfile(
        String id, String name, String entity, int bonusPercent, List<FeastRequest> requests) {
    /** Mobs that path, sit and stay harmless once the game suppresses their side effects. */
    public static final Set<String> ENTITIES =
            Set.of(
                    "WANDERING_TRADER",
                    "SNOW_GOLEM",
                    "FOX",
                    "CAT",
                    "PIG",
                    "WOLF",
                    "RABBIT",
                    "OCELOT",
                    "VILLAGER");

    public FeastGuestProfile {
        requests = requests == null ? List.of() : List.copyOf(requests);
        if (id == null
                || id.isBlank()
                || name == null
                || name.isBlank()
                || entity == null
                || !ENTITIES.contains(entity)
                || bonusPercent < 0
                || bonusPercent > 300
                || requests.isEmpty())
            throw new IllegalArgumentException("Invalid Crafting Feast special guest: " + id);
    }
}
