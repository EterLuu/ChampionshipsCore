package ink.ziip.championshipscore.api.test.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent test-only identity reservations, independent of the short-lived admission leases.
 * Losing a session must never turn a formerly admitted Bot into a normal Core database identity.
 */
public final class TestIdentityReservations {
    private static final String HEADER = "CC_TEST_IDENTITIES_V1";

    private record Identity(String name, UUID uuid) {
        Identity {
            name = Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9_]{3,16}"))
                throw new IllegalArgumentException("invalid test identity name");
            Objects.requireNonNull(uuid, "uuid");
        }
    }

    private final Path file;
    private Set<Identity> identities = Set.of();
    private Set<Identity> committed = Set.of();
    private Set<String> names = Set.of();
    private Set<UUID> uuids = Set.of();

    /**
     * Loads before Core's login listeners are installed; unreadable/corrupt state fails startup.
     */
    public TestIdentityReservations(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath();
        try {
            if (Files.notExists(this.file)) return;
            var lines = Files.readAllLines(this.file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !HEADER.equals(lines.getFirst()))
                throw new IOException("invalid test identity reservation header");
            var loaded = new HashSet<Identity>();
            for (String line : lines.subList(1, lines.size())) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 2) throw new IOException("invalid test identity reservation");
                var identity = new Identity(fields[0], UUID.fromString(fields[1]));
                if (!loaded.add(identity)) throw new IOException("duplicate test reservation");
            }
            publish(loaded);
            committed = identities;
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Cannot load test identity reservations: " + this.file, failure);
        }
    }

    private TestIdentityReservations() {
        file = null;
    }

    /** Pure model fixtures only; production always uses the data-directory-backed constructor. */
    public static TestIdentityReservations inMemory() {
        return new TestIdentityReservations();
    }

    public synchronized boolean contains(String username, UUID observedUuid) {
        return containsName(username) || uuids.contains(observedUuid);
    }

    public synchronized boolean containsName(String username) {
        return username != null && names.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    /** Commit the reservation before the caller exposes an admission lease. */
    public synchronized void reserve(String username, UUID uuid) {
        var identity = new Identity(username, uuid);
        if (committed.contains(identity)) return;
        var changed = new HashSet<>(identities);
        changed.add(identity);
        try {
            if (file != null) persist(changed);
            publish(changed);
            committed = identities;
        } catch (IOException failure) {
            // No lease may be admitted after a failed commit. Retain the local deny entry too.
            publish(changed);
            throw new UncheckedIOException("Cannot persist test identity reservation", failure);
        }
    }

    private void publish(Set<Identity> values) {
        identities = Set.copyOf(values);
        var nextNames = new HashSet<String>();
        var nextUuids = new HashSet<UUID>();
        values.forEach(
                identity -> {
                    nextNames.add(identity.name());
                    nextUuids.add(identity.uuid());
                });
        names = Set.copyOf(nextNames);
        uuids = Set.copyOf(nextUuids);
    }

    private void persist(Set<Identity> changed) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path pending = Files.createTempFile(parent, ".test-identities-", ".pending");
        try {
            var content = new StringBuilder(HEADER).append('\n');
            changed.stream()
                    .sorted(
                            Comparator.comparing(Identity::name)
                                    .thenComparing(i -> i.uuid().toString()))
                    .forEach(
                            i ->
                                    content.append(i.name())
                                            .append('\t')
                                            .append(i.uuid())
                                            .append('\n'));
            try (var channel =
                    FileChannel.open(
                            pending,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                var bytes = ByteBuffer.wrap(content.toString().getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            // A filesystem without atomic replacement cannot safely publish a test lease.
            Files.move(
                    pending,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
