package ink.ziip.championshipscore.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signed, short-lived test admission claims. The signed UUID is only an expectation. */
public final class TestLeaseToken {
    private static final String VERSION = "v1";
    private static final long MAX_TTL_SECONDS = 86_400;

    public record Claims(
            String serverId,
            UUID sessionId,
            long generation,
            String username,
            UUID profileUuid,
            long expiresAtEpochSecond,
            String nonce) {
        public Claims {
            if (serverId == null || !serverId.matches("[A-Za-z0-9._-]{1,64}"))
                throw new IllegalArgumentException("invalid server id");
            Objects.requireNonNull(sessionId, "sessionId");
            if (generation < 1) throw new IllegalArgumentException("invalid generation");
            username = AuthIdentity.normalizeUsername(username);
            Objects.requireNonNull(profileUuid, "profileUuid");
            if (expiresAtEpochSecond < 1) throw new IllegalArgumentException("invalid expiry");
            if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,128}"))
                throw new IllegalArgumentException("invalid nonce");
        }
    }

    private TestLeaseToken() {}

    public static String sign(Claims claims, String secret) {
        byte[] payload = canonical(claims).getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encoded = encoder.encodeToString(payload);
        return VERSION + "." + encoded + "." + encoder.encodeToString(mac(payload, secret));
    }

    public static Claims verify(
            String token,
            String secret,
            String expectedServerId,
            UUID expectedSessionId,
            long expectedGeneration,
            long nowEpochSecond) {
        Objects.requireNonNull(token, "token");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0]))
            throw new IllegalArgumentException("invalid lease token format");
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] payload = decoder.decode(parts[1]);
            byte[] signature = decoder.decode(parts[2]);
            if (!MessageDigest.isEqual(mac(payload, secret), signature))
                throw new IllegalArgumentException("invalid lease signature");
            String[] fields = new String(payload, StandardCharsets.UTF_8).split("\\n", -1);
            if (fields.length != 7) throw new IllegalArgumentException("invalid lease claims");
            Claims claims =
                    new Claims(
                            fields[0],
                            UUID.fromString(fields[1]),
                            Long.parseLong(fields[2]),
                            fields[3],
                            UUID.fromString(fields[4]),
                            Long.parseLong(fields[5]),
                            fields[6]);
            if (!claims.serverId().equals(expectedServerId))
                throw new IllegalArgumentException("lease is for another server");
            if (!claims.sessionId().equals(expectedSessionId)
                    || claims.generation() != expectedGeneration)
                throw new IllegalArgumentException("lease session generation mismatch");
            long remaining = claims.expiresAtEpochSecond() - nowEpochSecond;
            if (remaining <= 0 || remaining > MAX_TTL_SECONDS)
                throw new IllegalArgumentException("lease expired or exceeds maximum lifetime");
            return claims;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid lease token", failure);
        }
    }

    private static String canonical(Claims claims) {
        return claims.serverId()
                + "\n"
                + claims.sessionId()
                + "\n"
                + claims.generation()
                + "\n"
                + claims.username()
                + "\n"
                + claims.profileUuid()
                + "\n"
                + claims.expiresAtEpochSecond()
                + "\n"
                + claims.nonce();
    }

    private static byte[] mac(byte[] payload, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("lease signing key must be at least 32 bytes");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HmacSHA256 unavailable", failure);
        }
    }
}
