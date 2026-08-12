package ink.ziip.championshipscore.database.sync;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Versioned invalidation carried by the shared Core data-sync Redis Stream. */
public record DatabaseSyncEvent(
        UUID eventId,
        String sourceInstance,
        long createdAt,
        Set<DatabaseSyncDomain> domains,
        String reason
) {
    public static final String KIND = "database-sync";
    public static final int SCHEMA_VERSION = 1;

    public DatabaseSyncEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(sourceInstance, "sourceInstance");
        Objects.requireNonNull(domains, "domains");
        Objects.requireNonNull(reason, "reason");
        if (sourceInstance.isBlank()) throw new IllegalArgumentException("sourceInstance must not be blank");
        if (createdAt < 1L) throw new IllegalArgumentException("createdAt must be positive");
        if (domains.isEmpty()) throw new IllegalArgumentException("domains must not be empty");
        domains = Set.copyOf(domains);
    }

    public Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", KIND);
        fields.put("schema", Integer.toString(SCHEMA_VERSION));
        fields.put("eventId", eventId.toString());
        fields.put("sourceInstance", sourceInstance);
        fields.put("createdAt", Long.toString(createdAt));
        fields.put("domains", domains.stream().sorted().map(Enum::name).collect(Collectors.joining(",")));
        fields.put("reason", reason);
        return fields;
    }

    public static DatabaseSyncEvent parse(Map<String, String> fields) {
        Objects.requireNonNull(fields, "fields");
        if (!KIND.equals(fields.get("kind"))) throw new IllegalArgumentException("Unsupported sync kind");
        int schema = Integer.parseInt(required(fields, "schema"));
        if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported sync schema " + schema);
        EnumSet<DatabaseSyncDomain> domains = Arrays.stream(required(fields, "domains").split(","))
                .filter(value -> !value.isBlank())
                .map(DatabaseSyncDomain::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DatabaseSyncDomain.class)));
        return new DatabaseSyncEvent(UUID.fromString(required(fields, "eventId")),
                required(fields, "sourceInstance"), Long.parseLong(required(fields, "createdAt")),
                domains, fields.getOrDefault("reason", "unspecified"));
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing field " + key);
        return value;
    }
}
