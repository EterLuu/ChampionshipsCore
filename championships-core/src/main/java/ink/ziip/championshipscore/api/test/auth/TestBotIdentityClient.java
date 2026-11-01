package ink.ziip.championshipscore.api.test.auth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Calls the cc-web TEST_HARNESS API without accepting UUIDs from the Bot client. */
public final class TestBotIdentityClient {
    private static final String PATH = "/api/internal/test/bots/leases";
    private final URI baseUri;
    private final String keyId;
    private final byte[] secret;
    private final HttpClient client;
    private final Duration timeout;
    private final Gson gson = new Gson();

    public TestBotIdentityClient(
            String baseUrl,
            String keyId,
            String secret,
            boolean allowInsecurePrivateHttp,
            Duration connectTimeout,
            Duration requestTimeout) {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException("Bot identity API URL is blank");
        this.baseUri =
                URI.create(
                        baseUrl.endsWith("/")
                                ? baseUrl.substring(0, baseUrl.length() - 1)
                                : baseUrl);
        if (!"http".equalsIgnoreCase(baseUri.getScheme())
                && !"https".equalsIgnoreCase(baseUri.getScheme()))
            throw new IllegalArgumentException("Bot identity API URL must use HTTP or HTTPS");
        if ("http".equalsIgnoreCase(baseUri.getScheme())
                && !allowInsecurePrivateHttp
                && !isLoopback(baseUri.getHost()))
            throw new IllegalArgumentException("Non-loopback HTTP requires explicit opt-in");
        this.keyId = keyId;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32)
            throw new IllegalArgumentException("Bot identity HMAC secret is too short");
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.timeout = requestTimeout;
    }

    public List<Lease> leases(
            String serverId,
            UUID sessionId,
            long generation,
            List<String> usernames,
            int ttlSeconds)
            throws Exception {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("serverId", serverId);
        payload.put("sessionId", sessionId.toString());
        payload.put("generation", generation);
        payload.put("ttlSeconds", ttlSeconds);
        payload.put("usernames", usernames);
        String body = gson.toJson(payload);
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request =
                HttpRequest.newBuilder(baseUri.resolve(PATH))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("X-CC-Key-Id", keyId)
                        .header("X-CC-Timestamp", timestamp)
                        .header("X-CC-Request-Id", requestId)
                        .header("X-CC-Signature", sign("POST", PATH, timestamp, requestId, body))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException(
                    "Bot identity API returned HTTP " + response.statusCode());
        JsonElement root;
        try {
            root = JsonParser.parseString(response.body());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Bot identity API returned invalid JSON", failure);
        }
        JsonArray bots = null;
        if (root.isJsonObject()) {
            JsonElement candidate = root.getAsJsonObject().get("bots");
            if (candidate != null && candidate.isJsonArray()) bots = candidate.getAsJsonArray();
        }
        if (bots == null || bots.size() != usernames.size())
            throw new IllegalStateException("Bot identity API returned an incomplete lease list");
        Set<String> expected =
                usernames.stream()
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toSet());
        Set<String> returned = new HashSet<>();
        List<Lease> result = new ArrayList<>();
        for (JsonElement bot : bots) {
            if (!bot.isJsonObject()) throw new IllegalStateException("Bot lease is incomplete");
            var object = bot.getAsJsonObject();
            String username = object.has("username") ? object.get("username").getAsString() : "";
            String token = object.has("leaseToken") ? object.get("leaseToken").getAsString() : "";
            if (username.isBlank() || token.isBlank())
                throw new IllegalStateException("Bot lease is incomplete");
            if (!returned.add(username.toLowerCase(Locale.ROOT))
                    || !expected.contains(username.toLowerCase(Locale.ROOT)))
                throw new IllegalStateException(
                        "Bot identity API returned an unexpected lease username");
            result.add(new Lease(username, token));
        }
        if (!returned.equals(expected))
            throw new IllegalStateException("Bot identity API returned an incomplete lease list");
        return List.copyOf(result);
    }

    private String sign(String method, String path, String timestamp, String requestId, String body)
            throws Exception {
        byte[] bodyHash =
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return java.util.HexFormat.of()
                .formatHex(
                        mac.doFinal(
                                (method
                                                + "\n"
                                                + path
                                                + "\n"
                                                + timestamp
                                                + "\n"
                                                + requestId
                                                + "\n"
                                                + java.util.HexFormat.of().formatHex(bodyHash))
                                        .getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    public record Lease(String username, String token) {}
}
