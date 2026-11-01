package ink.ziip.championshipscore.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class TestLeaseTokenTest {
    private static final String SECRET = "isolated-test-signing-key-32-bytes-minimum";
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void verifiesClaimsOnlyForTargetServerSessionAndGeneration() {
        var claims =
                new TestLeaseToken.Claims(
                        "core-test", SESSION, 4, "Bot_00", PROFILE, 2000, "abcdefghijklmnop");
        String token = TestLeaseToken.sign(claims, SECRET);

        var verified = TestLeaseToken.verify(token, SECRET, "core-test", SESSION, 4, 1000);
        assertEquals("bot_00", verified.username());
        assertEquals(PROFILE, verified.profileUuid());
        assertThrows(
                IllegalArgumentException.class,
                () -> TestLeaseToken.verify(token, SECRET, "another-core", SESSION, 4, 1000));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestLeaseToken.verify(token, SECRET, "core-test", SESSION, 5, 1000));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TestLeaseToken.verify(
                                token,
                                "different-signing-key-that-is-long-enough",
                                "core-test",
                                SESSION,
                                4,
                                1000));
        assertThrows(
                IllegalArgumentException.class,
                () -> TestLeaseToken.verify(token, SECRET, "core-test", SESSION, 4, 2000));
    }
}
