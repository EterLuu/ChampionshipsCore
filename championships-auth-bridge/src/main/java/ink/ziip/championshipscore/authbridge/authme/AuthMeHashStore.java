package ink.ziip.championshipscore.authbridge.authme;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.security.crypts.HashedPassword;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.regex.Pattern;

/** The only compatibility boundary that accesses AuthMe's pre-hashed password storage. */
public final class AuthMeHashStore {
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final DataSource dataSource;

    public AuthMeHashStore() {
        this.dataSource = resolveDataSource(AuthMeApi.getInstance());
    }

    public void provision(String username, String passwordHash) {
        validate(username, passwordHash);
        String normalized = username.toLowerCase(Locale.ROOT);
        HashedPassword hash = new HashedPassword(passwordHash);
        boolean success;
        if (dataSource.isAuthAvailable(normalized)) {
            success = dataSource.updatePassword(normalized, hash);
        } else {
            PlayerAuth auth = PlayerAuth.builder()
                .name(normalized)
                .realName(username)
                .password(hash)
                .registrationDate(System.currentTimeMillis())
                .build();
            success = dataSource.saveAuth(auth);
        }
        dataSource.invalidateCache(normalized);
        if (!success) throw new IllegalStateException("AuthMe rejected account update for " + username);
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
    public void rename(String oldUsername, String newUsername) {
        validateUsername(oldUsername);
        validateUsername(newUsername);
        String oldNormalized = oldUsername.toLowerCase(Locale.ROOT);
        String newNormalized = newUsername.toLowerCase(Locale.ROOT);
        if (oldNormalized.equals(newNormalized)) {
            if (!dataSource.updateRealName(oldNormalized, newUsername)) throw new IllegalStateException("AuthMe rejected account rename for " + oldUsername);
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
            return;
        }
        PlayerAuth existing = dataSource.getAuth(oldNormalized);
        if (existing == null || existing.getPassword() == null) throw new IllegalStateException("AuthMe account data is unavailable for " + oldUsername);
        PlayerAuth renamed = PlayerAuth.builder()
            .name(newNormalized)
            .realName(newUsername)
            .password(existing.getPassword())
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
            .uuid(existing.getUuid())
            .build();
        if (!dataSource.saveAuth(renamed)) throw new IllegalStateException("AuthMe rejected account rename for " + newUsername);
        if (!dataSource.removeAuth(oldNormalized)) {
            dataSource.removeAuth(newNormalized);
            throw new IllegalStateException("AuthMe rejected removal of old account name " + oldUsername);
        }
        dataSource.invalidateCache(oldNormalized);
        dataSource.invalidateCache(newNormalized);
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
}
