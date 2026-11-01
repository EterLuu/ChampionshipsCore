package ink.ziip.championshipscore.api.test.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class TestLeaseNonceStoreTest {
    @TempDir Path directory;

    @Test
    void nonceRemainsConsumedAfterStoreRestart() {
        Path file = directory.resolve("test/lease-nonces.tsv");
        new TestLeaseNonceStore(file).consume("abcdefghijklmnop");

        assertThrows(
                IllegalStateException.class,
                () -> new TestLeaseNonceStore(file).consume("abcdefghijklmnop"));
    }

    @Test
    void malformedNonceStateFailsClosed() throws Exception {
        Path file = directory.resolve("nonces.tsv");
        java.nio.file.Files.writeString(file, "CC_TEST_LEASE_NONCES_V1\ninvalid\n");

        assertThrows(
                IllegalStateException.class,
                () -> new TestLeaseNonceStore(file).consume("abcdefghijklmnop"));
    }
}
