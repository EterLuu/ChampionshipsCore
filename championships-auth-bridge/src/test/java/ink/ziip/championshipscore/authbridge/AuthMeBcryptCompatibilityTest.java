package ink.ziip.championshipscore.authbridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.xephi.authme.security.crypts.BCryptHasher;

import org.junit.jupiter.api.Test;

class AuthMeBcryptCompatibilityTest {
    private static final String NODE_BCRYPT_HASH =
            "$2b$12$GtiEbG4k4mSMN4xthFlhdupr2PaOJR.qDRNl61kCGuDmng63iUlMa";

    @Test
    void authMeAcceptsNodeBcryptModularCryptFormat() {
        assertTrue(BCryptHasher.comparePassword("correct horse battery staple", NODE_BCRYPT_HASH));
        assertFalse(BCryptHasher.comparePassword("wrong password", NODE_BCRYPT_HASH));
    }
}
