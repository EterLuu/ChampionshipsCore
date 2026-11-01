package ink.ziip.championshipscore.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

final class BotLoginPolicyTest {
    @Test
    void restrictsBotNamesToConfiguredNetworksButLeavesNormalPlayersUnchanged() throws Exception {
        BotLoginPolicy policy = BotLoginPolicy.of("autobot", List.of("10.20.0.0/16"));
        assertTrue(policy.allows("regular", InetAddress.getByName("192.168.1.1")));
        assertTrue(policy.allows("autobot00", InetAddress.getByName("10.20.4.5")));
        assertFalse(policy.allows("autobot00", InetAddress.getByName("10.21.4.5")));
    }

    @Test
    void emptyNetworkIsFailClosedForBotNames() throws Exception {
        BotLoginPolicy policy = BotLoginPolicy.of("autobot", List.of());
        assertFalse(policy.allows("autobot00", InetAddress.getByName("127.0.0.1")));
    }

    @Test
    void authoritativePerBotMetadataOverridesTheLocalPrefixPolicy() throws Exception {
        BotLoginPolicy policy = BotLoginPolicy.of("autobot", List.of("10.20.0.0/16"));
        assertTrue(
                policy.allows(
                        "autobot00",
                        InetAddress.getByName("192.168.1.4"),
                        true,
                        List.of("192.168.0.0/16")));
        assertFalse(
                policy.allows(
                        "autobot00",
                        InetAddress.getByName("10.20.1.4"),
                        true,
                        List.of("192.168.0.0/16")));
        assertTrue(
                policy.allows("autobot00", InetAddress.getByName("203.0.113.4"), false, List.of()));
    }
}
