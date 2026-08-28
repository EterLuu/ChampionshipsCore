package ink.ziip.championshipscore.api.daily;

import org.jetbrains.annotations.NotNull;

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

/** Signs and submits leaderboard snapshots with the same HMAC contract as AuthBridge. */
public final class WebLeaderboardApiClient {
    private static final String PATH = "/api/internal/bridge/leaderboards";

    private final URI baseUri;
    private final String keyId;
    private final byte[] secret;
    private final Duration requestTimeout;
    private final HttpClient client;

    public WebLeaderboardApiClient(@NotNull String baseUrl,
                                   @NotNull String keyId,
                                   @NotNull String secret,
                                   boolean allowInsecurePrivateHttp,
                                   long connectTimeoutSeconds,
                                   long requestTimeoutSeconds) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String scheme = baseUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
            throw new IllegalArgumentException("Leaderboard API URL must use HTTP or HTTPS");
        if ("http".equalsIgnoreCase(scheme) && !isLoopback(baseUri.getHost()) && !allowInsecurePrivateHttp)
            throw new IllegalArgumentException("Non-loopback HTTP requires leaderboard-sync.allow-insecure-private-http=true");
        this.keyId = keyId;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32)
            throw new IllegalArgumentException("Leaderboard HMAC secret must contain at least 32 bytes");
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    public void submit(@NotNull WebLeaderboardSnapshot snapshot) throws Exception {
        String body = toJson(snapshot);
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(PATH))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-CC-Key-Id", keyId)
                .header("X-CC-Timestamp", timestamp)
                .header("X-CC-Request-Id", requestId)
                .header("X-CC-Signature", sign("POST", PATH, timestamp, requestId, body))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Leaderboard API returned HTTP " + response.statusCode());
    }


    private static @NotNull String toJson(@NotNull WebLeaderboardSnapshot snapshot) {
        StringBuilder json = new StringBuilder(1024);
        json.append("{\"games\":[");
        for (int i = 0; i < snapshot.games().size(); i++) {
            if (i != 0) json.append(',');
            appendQuoted(json, snapshot.games().get(i));
        }
        json.append("],\"boards\":[");
        for (int boardIndex = 0; boardIndex < snapshot.boards().size(); boardIndex++) {
            if (boardIndex != 0) json.append(',');
            WebLeaderboardSnapshot.WebLeaderboardBoard board = snapshot.boards().get(boardIndex);
            json.append("{\"game\":");
            appendQuoted(json, board.game());
            json.append(",\"metric\":");
            appendQuoted(json, board.metric());
            json.append(",\"map\":");
            if (board.map() == null) json.append("null");
            else appendQuoted(json, board.map());
            json.append(",\"format\":");
            appendQuoted(json, board.format());
            json.append(",\"lowerBetter\":").append(board.lowerBetter()).append(",\"entries\":[");
            for (int entryIndex = 0; entryIndex < board.entries().size(); entryIndex++) {
                if (entryIndex != 0) json.append(',');
                WebLeaderboardSnapshot.WebLeaderboardEntry entry = board.entries().get(entryIndex);
                json.append("{\"uuid\":");
                appendQuoted(json, entry.uuid());
                json.append(",\"username\":");
                appendQuoted(json, entry.username());
                json.append(",\"value\":").append(entry.value())
                        .append(",\"tieDurationMs\":").append(entry.tieDurationMs())
                        .append('}');
            }
            json.append("]}");
        }
        json.append("],\"generatedAt\":").append(snapshot.generatedAt()).append('}');
        return json.toString();
    }

    private static void appendQuoted(@NotNull StringBuilder json, @NotNull String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) json.append(String.format("\\u%04x", (int) character));
                    else json.append(character);
                }
            }
        }
        json.append('"');
    }

    private String sign(String method, String path, String timestamp, String requestId, String body)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String bodyHash = HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        String payload = method + "\n" + path + "\n" + timestamp + "\n" + requestId + "\n" + bodyHash;
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean isLoopback(String host) {
        return host != null && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1") || host.equals("::1"));
    }
}
