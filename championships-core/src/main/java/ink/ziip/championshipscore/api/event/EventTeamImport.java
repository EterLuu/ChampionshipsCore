package ink.ziip.championshipscore.api.event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record EventTeamImport(@NotNull Event event, @NotNull List<Team> teams) {
    public record Event(
            @NotNull String id,
            @NotNull String slug,
            @NotNull String title,
            @NotNull String lifecycleStatus,
            @NotNull List<Game> games,
            @NotNull List<Double> roundMultipliers) {}

    /** Version is optional on the wire; cc-web owns version selection. */
    public record Game(@NotNull String key, @Nullable String variantKey, @NotNull String label) {}

    public record Team(
            @NotNull String name,
            @NotNull String colorName,
            @NotNull String colorHex,
            @NotNull List<Member> members) {}

    public record Member(@NotNull String username, @NotNull String uuid, boolean isSubstitute) {
        public Member(@NotNull String username, @NotNull String uuid) {
            this(username, uuid, false);
        }
    }
}
