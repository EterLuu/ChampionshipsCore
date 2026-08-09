package ink.ziip.championshipscore.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** UUIDv5 identifiers used to make Redis redelivery and SCC score writes idempotent. */
public final class DeterministicIds {
    private DeterministicIds() {
    }

    public static UUID scoreTransaction(
            UUID matchId, long epoch, long seq, UUID playerId, String awardKind) {
        ProtocolSupport.required(playerId, "playerId");
        return uuidV5(matchId, epoch + "\u001f" + seq + "\u001f" + playerId + "\u001f"
                + ProtocolSupport.nonBlank(awardKind, "awardKind"));
    }

    public static UUID uuidV5(UUID namespace, String name) {
        ProtocolSupport.required(namespace, "namespace");
        ProtocolSupport.required(name, "name");
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(ByteBuffer.allocate(16)
                    .putLong(namespace.getMostSignificantBits())
                    .putLong(namespace.getLeastSignificantBits())
                    .array());
            byte[] digest = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x50);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            ByteBuffer bytes = ByteBuffer.wrap(digest);
            return new UUID(bytes.getLong(), bytes.getLong());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 is required by the Java runtime", impossible);
        }
    }
}
