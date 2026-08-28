package ink.ziip.championshipscore.authproxy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

final class ProxyIdentityClient {
    private static final String LOGIN_PROFILE_PREFIX = "/api/internal/bridge/login-profile/";
    private static final String PROXY_CHANGES_PATH = "/api/internal/bridge/proxy-changes";
    private static final String PROXY_BAN_SNAPSHOT_PATH = "/api/internal/bridge/proxy-ban-snapshot";

    private final URI baseUri;
    private final String keyId;
    private final byte[] secret;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    ProxyIdentityClient(String baseUrl, String keyId, String secret, boolean allowInsecurePrivateHttp,
                        Duration connectTimeout, Duration requestTimeout) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String scheme = baseUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Bridge API URL must use HTTP or HTTPS");
        }
        if ("http".equalsIgnoreCase(scheme) && !isLoopback(baseUri.getHost()) && !allowInsecurePrivateHttp) {
            throw new IllegalArgumentException("Non-loopback HTTP requires api.allow-insecure-private-http=true");
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Bridge HMAC secret must contain at least 32 bytes");
        }
        this.keyId = keyId;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    LoginProfile lookup(String username) throws Exception {
        if (!username.matches("^[A-Za-z0-9_]{3,16}$")) throw new IllegalArgumentException("Invalid Minecraft username");
        String path = LOGIN_PROFILE_PREFIX + username;
        return signedGet(path, LoginProfile.class);
    }

    ProxyBanSnapshot banSnapshot() throws Exception {
        return signedGet(PROXY_BAN_SNAPSHOT_PATH, ProxyBanSnapshot.class);
    }

    ProxyChangeBatch changesAfter(String cursor) throws Exception {
        if (cursor == null || !cursor.matches("^\\d{1,19}$")) throw new IllegalArgumentException("Invalid bridge cursor");
        return signedGet(PROXY_CHANGES_PATH + "?after=" + cursor + "&limit=100", PROXY_CHANGES_PATH, ProxyChangeBatch.class);
    }

    private <T> T signedGet(String path, Class<T> responseType) throws Exception {
        return signedGet(path, path, responseType);
    }

    private <T> T signedGet(String requestPath, String signaturePath, Class<T> responseType) throws Exception {
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(requestPath))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("X-CC-Key-Id", keyId)
                .header("X-CC-Timestamp", timestamp)
                .header("X-CC-Request-Id", requestId)
                .header("X-CC-Signature", sign("GET", signaturePath, timestamp, requestId, ""))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("Bridge API returned HTTP " + response.statusCode());
        T profile = mapper.readValue(response.body(), responseType);
        if (profile instanceof LoginProfile loginProfile) validateLoginProfile(loginProfile);
        return profile;
    }

    private static void validateLoginProfile(LoginProfile profile) {
        if (profile.status == null) throw new IllegalStateException("Bridge login profile omitted status");
        if ("ALLOWED".equals(profile.status)) {
            if (profile.uuid == null) throw new IllegalStateException("Allowed login profile omitted UUID");
            UUID.fromString(profile.uuid);
        }
    }

    private String sign(String method, String path, String timestamp, String requestId, String body) throws Exception {
        String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((method + "\n" + path + "\n" + timestamp + "\n" + requestId + "\n" + bodyHash).getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
    }

    static final class LoginProfile {
        public String status;
        public String uuid;
        public String reason;
        public String expiresAt;
    }

    static final class ProxyBanSnapshot {
        public Boolean maintenance;
        public String nextCursor;
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
        public String reason;
        public String expiresAt;
    }
}
