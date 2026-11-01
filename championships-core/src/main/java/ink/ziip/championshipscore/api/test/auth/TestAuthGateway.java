package ink.ziip.championshipscore.api.test.auth;

import ink.ziip.championshipscore.api.test.TestIdentityRegistry;
import ink.ziip.championshipscore.api.test.TestSession;
import ink.ziip.championshipscore.api.test.TestSessionState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure Core-side boundary between server pre-login observations and AuthMe authentication.
 *
 * <p>It does not perform network calls and never accepts a UUID as an authority from the Bot.
 */
public final class TestAuthGateway {
    public enum PreLoginStatus {
        NOT_TEST_IDENTITY,
        ACCEPTED,
        REJECTED
    }

    public record PreLoginResult(
            PreLoginStatus status, TestIdentityRegistry.BindingSnapshot binding, String reason) {
        public boolean accepted() {
            return status == PreLoginStatus.ACCEPTED;
        }
    }

    private final TestSession session;

    public TestAuthGateway(TestSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Observes Paper/AuthBridge's actual UUID before AuthMe's LoginEvent. */
    public synchronized PreLoginResult observePreLogin(
            String username, UUID serverObservedUuid, Instant now) {
        TestIdentityRegistry.BindingSnapshot existing = session.identities().snapshot(username);
        if (existing == null)
            return new PreLoginResult(PreLoginStatus.NOT_TEST_IDENTITY, null, "no test lease");
        if (session.state().terminal())
            return new PreLoginResult(
                    PreLoginStatus.REJECTED, existing, "test session is terminal");
        try {
            TestIdentityRegistry.BindingSnapshot binding =
                    session.identities()
                            .bindObserved(
                                    session.sessionId(),
                                    session.generation(),
                                    username,
                                    serverObservedUuid,
                                    now);
            return new PreLoginResult(PreLoginStatus.ACCEPTED, binding, "observed");
        } catch (RuntimeException failure) {
            return new PreLoginResult(PreLoginStatus.REJECTED, existing, failure.getMessage());
        }
    }

    /** Marks a pre-login observation usable only after AuthMe has accepted the same UUID. */
    public synchronized TestIdentityRegistry.BindingSnapshot markAuthMeLogin(
            String username, UUID serverObservedUuid, Instant now) {
        if (session.state() == TestSessionState.FAILED
                || session.state() == TestSessionState.CLOSED)
            throw new IllegalStateException("test session is terminal");
        return session.identities()
                .markAuthenticated(
                        session.sessionId(),
                        session.generation(),
                        username,
                        serverObservedUuid,
                        now);
    }
}
