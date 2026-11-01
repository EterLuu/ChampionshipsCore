package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class TestIdentityRegistryTest {
    @Test
    void onlyTheServerObservedUuidCanAuthenticateTheLease() {
        UUID session = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        TestIdentityRegistry registry = new TestIdentityRegistry();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        registry.register(
                new TestIdentityRegistry.Lease(
                        session, 1, "TestBot", expected, now.plusSeconds(60)),
                now);

        assertThrows(
                IllegalStateException.class,
                () -> registry.bindObserved(session, 1, "testbot", UUID.randomUUID(), now));
        TestIdentityRegistry.BindingSnapshot binding =
                registry.bindObserved(session, 1, "TESTBOT", expected, now);
        assertEquals(expected, binding.observedUuid());
        assertEquals(TestIdentityRegistry.BindingState.OBSERVED, binding.state());
        binding = registry.markAuthenticated(session, 1, "TESTBOT", expected, now);
        assertEquals(TestIdentityRegistry.BindingState.AUTHENTICATED, binding.state());
    }

    @Test
    void rejectsStaleGenerationAndExpiredLease() {
        UUID session = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        TestIdentityRegistry registry = new TestIdentityRegistry();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        registry.register(
                new TestIdentityRegistry.Lease(session, 2, "botone", expected, now.plusSeconds(1)),
                now);

        assertThrows(
                IllegalStateException.class,
                () -> registry.bindObserved(session, 1, "botone", expected, now));
        assertThrows(
                IllegalStateException.class,
                () -> registry.bindObserved(session, 2, "botone", expected, now.plusSeconds(1)));
    }

    @Test
    void revokedSessionCannotBeReusedAndNamesCanReceiveAFreshLease() {
        UUID session = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        TestIdentityRegistry registry = new TestIdentityRegistry();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        registry.register(
                new TestIdentityRegistry.Lease(session, 1, "botone", expected, now.plusSeconds(60)),
                now);
        registry.bindObserved(session, 1, "botone", expected, now);
        registry.revokeSession(session, 1);

        assertEquals(0, registry.size());
        assertThrows(
                IllegalStateException.class,
                () -> registry.bindObserved(session, 1, "botone", expected, now));
        UUID nextExpected = UUID.randomUUID();
        registry.register(
                new TestIdentityRegistry.Lease(
                        UUID.randomUUID(), 1, "BOTONE", nextExpected, now.plusSeconds(60)),
                now);
        assertEquals(1, registry.size());
    }

    @Test
    void sameObservedUuidMayRetryAfterDisconnectBeforeAuthMe() {
        UUID session = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        TestIdentityRegistry registry = new TestIdentityRegistry();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        registry.register(
                new TestIdentityRegistry.Lease(session, 1, "botone", expected, now.plusSeconds(60)),
                now);

        registry.bindObserved(session, 1, "botone", expected, now);
        TestIdentityRegistry.BindingSnapshot retry =
                registry.bindObserved(session, 1, "botone", expected, now.plusSeconds(1));

        assertEquals(TestIdentityRegistry.BindingState.OBSERVED, retry.state());
        assertEquals(expected, retry.observedUuid());
    }

    @Test
    void renewalExtendsOnlyTheSameAuthenticatedIdentity() {
        UUID session = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        TestIdentityRegistry registry = new TestIdentityRegistry();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        registry.register(
                new TestIdentityRegistry.Lease(session, 1, "botone", expected, now.plusSeconds(60)),
                now);
        registry.bindObserved(session, 1, "botone", expected, now);
        registry.markAuthenticated(session, 1, "botone", expected, now);

        TestIdentityRegistry.BindingSnapshot renewed =
                registry.renew(
                        session,
                        1,
                        "botone",
                        expected,
                        now.plusSeconds(86_400),
                        now.plusSeconds(1));

        assertEquals(TestIdentityRegistry.BindingState.AUTHENTICATED, renewed.state());
        assertEquals(now.plusSeconds(86_400), renewed.expiresAt());
        assertThrows(
                IllegalStateException.class,
                () ->
                        registry.renew(
                                session,
                                1,
                                "botone",
                                UUID.randomUUID(),
                                now.plusSeconds(86_400),
                                now.plusSeconds(1)));
    }
}
