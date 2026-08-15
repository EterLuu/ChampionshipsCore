package ink.ziip.championshipscore.database;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * One ordered, idempotent schema change. Bodies stay idempotent (guarded ALTER / IF NOT EXISTS)
 * because databases upgraded before the controller existed carry arbitrary partial states that
 * must reconcile cleanly the first time the version table is populated.
 */
public record DatabaseMigration(int version, @NotNull String name, @NotNull Body body) {

    @FunctionalInterface
    public interface Body {
        void apply(@NotNull Connection connection) throws SQLException;
    }

    public DatabaseMigration {
        if (version < 1) throw new IllegalArgumentException("migration version must be positive");
        if (name.isBlank()) throw new IllegalArgumentException("migration name must not be blank");
    }
}
