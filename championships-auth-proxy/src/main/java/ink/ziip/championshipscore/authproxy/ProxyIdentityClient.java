package ink.ziip.championshipscore.authproxy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import ink.ziip.championshipscore.auth.AuthIdentity;
import ink.ziip.championshipscore.auth.BotLoginPolicy;
import ink.ziip.championshipscore.auth.SignedRequests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

final class ProxyIdentityClient implements AutoCloseable {
    private static final String LOGIN_PROFILE_PREFIX = "/api/internal/bridge/login-profile/";
    private static final String PROXY_CHANGES_PATH = "/api/internal/bridge/proxy-changes";
    private static final String PROXY_BAN_SNAPSHOT_PATH = "/api/internal/bridge/proxy-ban-snapshot";

    private final URI baseUri;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final SignedRequests requests;
    private final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    ProxyIdentityClient(
            String baseUrl,
            String keyId,
            String secret,
            boolean allowInsecurePrivateHttp,
            Duration connectTimeout,
            Duration requestTimeout) {
        this.baseUri = SignedRequests.baseUri(baseUrl, allowInsecurePrivateHttp);
        this.requests = new SignedRequests(keyId, secret);
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    LoginProfile lookup(String username) throws Exception {
        AuthIdentity.requireUsername(username);
        String path = LOGIN_PROFILE_PREFIX + username;
        return signedGet(path, LoginProfile.class);
    }

    ProxyBanSnapshot banSnapshot() throws Exception {
        return signedGet(PROXY_BAN_SNAPSHOT_PATH, ProxyBanSnapshot.class);
    }

    ProxyChangeBatch changesAfter(String cursor) throws Exception {
        return signedGet(
                PROXY_CHANGES_PATH + "?after=" + SignedRequests.cursor(cursor) + "&limit=100",
                ProxyChangeBatch.class);
    }

    private <T> T signedGet(String requestPath, Class<T> responseType) throws Exception {
        HttpRequest request = requests.get(baseUri.resolve(requestPath), requestTimeout);
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
            throw new BridgeHttpException(response.statusCode(), errorCode(response.body()));
        T profile = mapper.readValue(response.body(), responseType);
        if (profile instanceof LoginProfile loginProfile) validateLoginProfile(loginProfile);
        return profile;
    }

    private static void validateLoginProfile(LoginProfile profile) {
        if (profile.status == null)
            throw new IllegalStateException("Bridge login profile omitted status");
        if (!Set.of("ALLOWED", "UNBOUND", "BANNED", "REVOKED", "MAINTENANCE")
                .contains(profile.status)) {
            throw new IllegalStateException("Bridge login profile returned unsupported status");
        }
        if ("ALLOWED".equals(profile.status)) {
            if (profile.uuid == null)
                throw new IllegalStateException("Allowed login profile omitted UUID");
            AuthIdentity.parseUuid(profile.uuid, "login profile UUID");
        }
        if (Boolean.TRUE.equals(profile.bot)) {
            if (profile.allowedCidrs == null)
                throw new IllegalStateException("Bot login profile omitted CIDR policy");
            // Parse now so malformed trusted metadata cannot poison the durable fallback cache.
            BotLoginPolicy.of("autobot", profile.allowedCidrs);
        }
    }

    /** cc-web reports stable error codes in Nest's {@code message} field. */
    private String errorCode(String body) {
        try {
            String message = mapper.readTree(body).path("message").asText(null);
            return message != null && message.matches("^[A-Z][A-Z0-9_]{1,63}$") ? message : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean isServiceUnavailable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.io.IOException) return true;
            if (current instanceof BridgeHttpException http
                    && (http.statusCode == 429 || http.statusCode >= 500)) return true;
            current = current.getCause();
        }
        return false;
    }

    static final class LoginProfile {
        public String username;
        public String status;
        public String uuid;
        public Boolean bot;
        public List<String> allowedCidrs;
        public String reason;
        public String expiresAt;
    }

    static final class ProxyBanSnapshot {
        public Boolean maintenance;
        public String nextCursor;
        public java.util.List<LoginProfile> profiles;
        public java.util.List<ProxyBan> bans;
    }

    static final class ProxyBan {
        public String username;
        public String reason;
        public String expiresAt;
    }

    static final class ProxyChangeBatch {
        public Boolean maintenance;
        public java.util.List<ProxyChange> changes;
        public String nextCursor;
    }

    static final class ProxyChange {
        public String operation;
        public String authmeUsername;
        public String previousUsername;
        public String status;
        public String uuid;
        public Boolean bot;
        public List<String> allowedCidrs;
        public String reason;
        public String expiresAt;
    }

    static final class BridgeHttpException extends Exception {
        private final int statusCode;
        private final String code;

        BridgeHttpException(int statusCode, String code) {
            super(
                    "Bridge API returned HTTP "
                            + statusCode
                            + (code == null ? "" : " (" + code + ")"));
            this.statusCode = statusCode;
            this.code = code;
        }

        /**
         * The event cursor is unusable: beyond every id cc-web has issued (its database was
         * restored from a backup) or malformed. Only a fresh snapshot can recover from it.
         */
        boolean rejectsCursor() {
            return (statusCode == 409 && "CURSOR_AHEAD_OF_OUTBOX".equals(code))
                    || (statusCode == 400 && "INVALID_CURSOR".equals(code));
        }
    }

    @Override
    public void close() {
        client.shutdownNow();
    }
}
