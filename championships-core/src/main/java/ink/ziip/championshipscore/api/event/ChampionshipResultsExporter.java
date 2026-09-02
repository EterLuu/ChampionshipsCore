package ink.ziip.championshipscore.api.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ink.ziip.championshipscore.api.rank.ChampionshipArchiveSnapshot;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ChampionshipResultsExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ChampionshipResultsExporter() {}

    public static @NotNull Path export(@NotNull Path dataDirectory, @NotNull String eventSlug,
                                       @NotNull ChampionshipArchiveSnapshot snapshot) throws IOException {
        if (!eventSlug.matches("[a-z0-9][a-z0-9-]{1,31}"))
            throw new IllegalArgumentException("Invalid championship slug");
        Path exports = dataDirectory.resolve("exports");
        Files.createDirectories(exports);
        Path target = exports.resolve(eventSlug + "-results.json");
        Path temporary = Files.createTempFile(exports, eventSlug + "-results-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(snapshot) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toAbsolutePath().normalize();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
