package ink.ziip.championshipscore.api.game.config.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Variant-owned presentation content; spatial introduction points remain part of the map geometry. */
public record GamePresentationSettings(List<List<String>> ruleSections) {
    public GamePresentationSettings {
        if (ruleSections == null) {
            ruleSections = List.of();
        } else {
            List<List<String>> copy = new ArrayList<>();
            for (List<String> section : ruleSections)
                copy.add(section == null ? List.of() : List.copyOf(section));
            ruleSections = Collections.unmodifiableList(copy);
        }
    }
}
