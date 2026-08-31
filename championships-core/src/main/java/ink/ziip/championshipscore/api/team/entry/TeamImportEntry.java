package ink.ziip.championshipscore.api.team.entry;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public record TeamImportEntry(@NotNull String name, @NotNull String colorName,
                              @NotNull String colorCode, @NotNull List<Member> members) {
    public record Member(@NotNull UUID uuid, @NotNull String username) {
    }
}
