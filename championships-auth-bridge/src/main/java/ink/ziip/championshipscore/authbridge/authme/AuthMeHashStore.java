package ink.ziip.championshipscore.authbridge.authme;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.security.crypts.HashedPassword;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** The only compatibility boundary that accesses AuthMe's pre-hashed password storage. */
public final class AuthMeHashStore {
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final DataSource dataSource;

    public AuthMeHashStore() {
        this.dataSource = resolveDataSource(AuthMeApi.getInstance());
    }

    public void provision(String username, String passwordHash, UUID uuid) {
        validate(username, passwordHash);
        if (uuid == null) throw new IllegalArgumentException("AuthMe UUID is required");
        String normalized = username.toLowerCase(Locale.ROOT);
        HashedPassword hash = new HashedPassword(passwordHash);
        boolean success;
        if (dataSource.isAuthAvailable(normalized)) {
            PlayerAuth existing = requireAuth(normalized);
            if (uuid.equals(existing.getUuid())) {
                success = dataSource.updatePassword(normalized, hash);
            } else {
                replace(existing, normalized, username, hash, uuid);
                success = true;
            }
        } else {
            PlayerAuth auth = PlayerAuth.builder()
                .name(normalized)
                .realName(username)
                .password(hash)
                .registrationDate(System.currentTimeMillis())
                .uuid(uuid)
                .build();
            success = dataSource.saveAuth(auth);
        }
        dataSource.invalidateCache(normalized);
        if (!success) throw new IllegalStateException("AuthMe rejected account update for " + username);
    }

    /** Updates only the password of an existing AuthMe row; identity is untouched. */
    public void updatePassword(String username, String passwordHash) {
        validate(username, passwordHash);
        String normalized = username.toLowerCase(Locale.ROOT);
        if (!dataSource.isAuthAvailable(normalized)) {
            throw new IllegalStateException("AuthMe account is missing for " + username);
        }
        requireAuth(normalized);
        if (!dataSource.updatePassword(normalized, new HashedPassword(passwordHash))) {
            throw new IllegalStateException("AuthMe rejected password update for " + username);
        }
        dataSource.invalidateCache(normalized);
    }

    public void remove(String username) {
        String normalized = username.toLowerCase(Locale.ROOT);
        if (dataSource.isAuthAvailable(normalized) && !dataSource.removeAuth(normalized)) {
            throw new IllegalStateException("AuthMe rejected account removal for " + username);
        }
        dataSource.invalidateCache(normalized);
    }

    /**
     * AuthMe exposes no rename operation for the primary login key. Migrate the
     * row through its DataSource boundary, or keep an already-registered target
     * row as canonical, so the website never has to connect to AuthMe directly.
     */
    public void rename(String oldUsername, String newUsername, UUID uuid) {
        validateUsername(oldUsername);
        validateUsername(newUsername);
        if (uuid == null) throw new IllegalArgumentException("AuthMe UUID is required");
        String oldNormalized = oldUsername.toLowerCase(Locale.ROOT);
        String newNormalized = newUsername.toLowerCase(Locale.ROOT);
        if (oldNormalized.equals(newNormalized)) {
            if (!dataSource.updateRealName(oldNormalized, newUsername)) throw new IllegalStateException("AuthMe rejected account rename for " + oldUsername);
            ensureUuid(oldNormalized, newUsername, uuid);
            dataSource.invalidateCache(oldNormalized);
            return;
        }
        boolean oldAvailable = dataSource.isAuthAvailable(oldNormalized);
        boolean newAvailable = dataSource.isAuthAvailable(newNormalized);
        if (!oldAvailable) {
            // The bridge may have completed the database rename just before a
            // process interruption. Treat that state as an idempotent success.
            if (newAvailable) {
                if (!dataSource.updateRealName(newNormalized, newUsername)) throw new IllegalStateException("AuthMe rejected account rename for " + newUsername);
                ensureUuid(newNormalized, newUsername, uuid);
                dataSource.invalidateCache(newNormalized);
                return;
            }
            throw new IllegalStateException("AuthMe account is missing for " + oldUsername);
        }
        if (newAvailable) {
            /*
             * The player may already have logged in with the approved name
             * before the bridge consumed the change event. In that case AuthMe
             * already owns the canonical account row, so do not recreate it
             * (which would fail on the unique login-name key) or replace its
             * password/session data. Keep that account and remove only the
             * obsolete old-name row. A failed cleanup is safe to retry because
             * the target row is left intact.
             */
            if (!dataSource.updateRealName(newNormalized, newUsername)) {
                throw new IllegalStateException("AuthMe rejected account rename for " + newUsername);
            }
            if (!dataSource.removeAuth(oldNormalized)) {
                throw new IllegalStateException("AuthMe rejected removal of old account name " + oldUsername);
            }
            dataSource.invalidateCache(oldNormalized);
            dataSource.invalidateCache(newNormalized);
            ensureUuid(newNormalized, newUsername, uuid);
            return;
        }
        PlayerAuth existing = requireAuth(oldNormalized);
        PlayerAuth renamed = copy(existing, newNormalized, newUsername, existing.getPassword(), uuid);
        if (!dataSource.saveAuth(renamed)) throw new IllegalStateException("AuthMe rejected account rename for " + newUsername);
        if (!dataSource.removeAuth(oldNormalized)) {
            dataSource.removeAuth(newNormalized);
            throw new IllegalStateException("AuthMe rejected removal of old account name " + oldUsername);
        }
        dataSource.invalidateCache(oldNormalized);
        dataSource.invalidateCache(newNormalized);
    }

    /** Imports only rows that are absent locally; existing AuthMe data is deliberately untouched. */
    public ReconcileResult importMissing(List<ProvisionAccount> accounts) {
        int changed = 0;
        for (ProvisionAccount account : accounts) {
            validate(account.username(), account.passwordHash());
            if (account.uuid() == null) throw new IllegalArgumentException("AuthMe UUID is required");
            String normalized = account.username().toLowerCase(Locale.ROOT);
            if (dataSource.isAuthAvailable(normalized)) continue;
            PlayerAuth auth = PlayerAuth.builder()
                    .name(normalized)
                    .realName(account.username())
                    .password(new HashedPassword(account.passwordHash()))
                    .registrationDate(System.currentTimeMillis())
                    .uuid(account.uuid())
                    .build();
            if (!dataSource.saveAuth(auth)) throw new IllegalStateException("AuthMe rejected account import for " + account.username());
            dataSource.invalidateCache(normalized);
            changed++;
        }
        return new ReconcileResult(accounts.size(), changed, 0);
    }

    /** Removes AuthMe rows whose normalized login name is absent from the cc-web allowlist. */
    public ReconcileResult removeUnknown(Set<String> desiredUsernames) {
        Set<String> desired = new HashSet<>();
        for (String username : desiredUsernames) {
            validateUsername(username);
            desired.add(username.toLowerCase(Locale.ROOT));
        }
        List<PlayerAuth> all = dataSource.getAllAuths();
        int removed = 0;
        for (PlayerAuth auth : all) {
            String normalized = auth.getNickname().toLowerCase(Locale.ROOT);
            if (desired.contains(normalized)) continue;
            if (!dataSource.removeAuth(normalized)) throw new IllegalStateException("AuthMe rejected account removal for " + auth.getNickname());
            dataSource.invalidateCache(normalized);
            removed++;
        }
        return new ReconcileResult(all.size(), removed, 0);
    }

    /** Changes only AuthMe's UUID column and preserves passwords, sessions and profile data. */
    public ReconcileResult migrateUuids(List<UuidMigration> migrations) {
        int changed = 0;
        int missing = 0;
        for (UuidMigration migration : migrations) {
            validateUsername(migration.username());
            if (migration.fromUuid() == null || migration.toUuid() == null) {
                throw new IllegalArgumentException("AuthMe UUID migration requires source and target UUIDs");
            }
            String normalized = migration.username().toLowerCase(Locale.ROOT);
            if (!dataSource.isAuthAvailable(normalized)) {
                missing++;
                continue;
            }
            PlayerAuth existing = requireAuth(normalized);
            if (migration.toUuid().equals(existing.getUuid())) continue;
            if (existing.getUuid() != null && !migration.fromUuid().equals(existing.getUuid())) {
                throw new IllegalStateException("AuthMe account has an unexpected identity for " + migration.username());
            }
            replace(existing, normalized, migration.username(), existing.getPassword(), migration.toUuid());
            changed++;
        }
        return new ReconcileResult(migrations.size(), changed, missing);
    }

    private void ensureUuid(String normalized, String realName, UUID uuid) {
        PlayerAuth existing = requireAuth(normalized);
        if (!uuid.equals(existing.getUuid())) replace(existing, normalized, realName, existing.getPassword(), uuid);
    }

    private PlayerAuth requireAuth(String normalized) {
        PlayerAuth auth = dataSource.getAuth(normalized);
        if (auth == null || auth.getPassword() == null) throw new IllegalStateException("AuthMe account data is unavailable for " + normalized);
        return auth;
    }

    private void replace(PlayerAuth existing, String normalized, String realName,
                         HashedPassword password, UUID uuid) {
        PlayerAuth replacement = copy(existing, normalized, realName, password, uuid);
        if (!dataSource.removeAuth(normalized)) throw new IllegalStateException("AuthMe rejected account replacement for " + realName);
        if (!dataSource.saveAuth(replacement)) {
            dataSource.saveAuth(existing);
            throw new IllegalStateException("AuthMe rejected UUID update for " + realName);
        }
        dataSource.invalidateCache(normalized);
    }

    private static PlayerAuth copy(PlayerAuth existing, String normalized, String realName,
                                   HashedPassword password, UUID uuid) {
        return PlayerAuth.builder()
                .name(normalized)
                .realName(realName)
                .password(password)
                .totpKey(existing.getTotpKey())
                .lastIp(existing.getLastIp())
                .email(existing.getEmail())
                .groupId(existing.getGroupId())
                .lastLogin(existing.getLastLogin())
                .registrationIp(existing.getRegistrationIp())
                .registrationDate(existing.getRegistrationDate())
                .locX(existing.getQuitLocX())
                .locY(existing.getQuitLocY())
                .locZ(existing.getQuitLocZ())
                .locWorld(existing.getWorld())
                .locYaw(existing.getYaw())
                .locPitch(existing.getPitch())
                .uuid(uuid)
                .build();
    }

    private static void validate(String username, String passwordHash) {
        validateUsername(username);
        if (passwordHash == null || !BCRYPT.matcher(passwordHash).matches()) {
            throw new IllegalArgumentException("Bridge accepts BCrypt hashes only");
        }
    }

    private static void validateUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Invalid Minecraft username");
    }

    private static DataSource resolveDataSource(AuthMeApi api) {
        try {
            Field field = AuthMeApi.class.getDeclaredField("dataSource");
            field.setAccessible(true);
            Object value = field.get(api);
            if (value instanceof DataSource source) return source;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("AuthMe no longer exposes the expected data source boundary", exception);
        }
        throw new IllegalStateException("AuthMe data source is unavailable");
    }

    public record ProvisionAccount(String username, String passwordHash, UUID uuid) {
    }

    public record UuidMigration(String username, UUID fromUuid, UUID toUuid) {
    }

    public record ReconcileResult(int examined, int changed, int missing) {
    }
}
