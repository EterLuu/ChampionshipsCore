package ink.ziip.championshipscore.api.event;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** cc-web tournament transport for one-time bearer team-import links. */
public final class WebEventApiClient {
    private static final String IMPORT_PREFIX = "/api/internal/championships/team-import/";
    private static final String RESULTS_PATH = "/api/internal/championships/results";
    private final URI baseUri;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final Gson gson = new Gson();
    private final String keyId;
    private final byte[] secret;

    public WebEventApiClient(
            @NotNull String baseUrl,
            boolean allowInsecurePrivateHttp,
            long connectTimeoutSeconds,
            long requestTimeoutSeconds) {
        this(
                baseUrl,
                "",
                "",
                allowInsecurePrivateHttp,
                connectTimeoutSeconds,
                requestTimeoutSeconds);
    }

    public WebEventApiClient(
            @NotNull String baseUrl,
            @NotNull String keyId,
            @NotNull String secret,
            boolean allowInsecurePrivateHttp,
            long connectTimeoutSeconds,
            long requestTimeoutSeconds) {
        baseUri =
                URI.create(
                        baseUrl.endsWith("/")
                                ? baseUrl.substring(0, baseUrl.length() - 1)
                                : baseUrl);
        String scheme = baseUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
            throw new IllegalArgumentException("Event API URL must use HTTP or HTTPS");
        if ("http".equalsIgnoreCase(scheme)
                && !isLoopback(baseUri.getHost())
                && !allowInsecurePrivateHttp)
            throw new IllegalArgumentException(
                    "Non-loopback HTTP requires leaderboard-sync.allow-insecure-private-http=true");
        requestTimeout = Duration.ofSeconds(Math.max(1, requestTimeoutSeconds));
        this.keyId = keyId;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                        .build();
    }

    /** Uploads an exported result file using the shared HMAC bridge contract. */
    public void submitResults(@NotNull Path exported) throws Exception {
        if (keyId.isBlank() || secret.length < 32)
            throw new IllegalStateException("Event result upload credentials are not configured");
        String body = normalizeResultWireBody(Files.readString(exported, StandardCharsets.UTF_8));
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request =
                HttpRequest.newBuilder(baseUri.resolve(RESULTS_PATH))
                        .timeout(requestTimeout)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("X-CC-Key-Id", keyId)
                        .header("X-CC-Timestamp", timestamp)
                        .header("X-CC-Request-Id", requestId)
                        .header(
                                "X-CC-Signature",
                                sign("POST", RESULTS_PATH, timestamp, requestId, body))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(response, "Event results upload");
    }

    public @NotNull EventTeamImport fetchTeamImport(@NotNull String rawLink) throws Exception {
        URI link = URI.create(rawLink);
        validateImportLink(link);
        HttpRequest request =
                HttpRequest.newBuilder(link)
                        .timeout(requestTimeout)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(response, "Team import");
        try {
            EventTeamImport imported = gson.fromJson(response.body(), EventTeamImport.class);
            if (imported == null || imported.event() == null || imported.teams() == null)
                throw new IllegalStateException("Team import response is incomplete");
            return imported;
        } catch (JsonParseException failure) {
            throw new IllegalStateException("Team import response is invalid JSON", failure);
        }
    }

    private void validateImportLink(URI link) {
        if (!sameOrigin(baseUri, link)
                || link.getRawQuery() != null
                || link.getRawFragment() != null
                || link.getPath() == null
                || !link.getPath().startsWith(IMPORT_PREFIX))
            throw new IllegalArgumentException(
                    "Import link must be generated by the configured cc-web origin");
        String token = link.getPath().substring(IMPORT_PREFIX.length());
        if (!token.matches("[A-Za-z0-9_-]{40,80}"))
            throw new IllegalArgumentException("Import link token is invalid");
    }

    private static void requireSuccess(HttpResponse<String> response, String operation) {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException(
                    operation + " API returned HTTP " + response.statusCode());
    }

    private String sign(String method, String path, String timestamp, String requestId, String body)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String bodyHash =
                HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        String payload =
                method + "\n" + path + "\n" + timestamp + "\n" + requestId + "\n" + bodyHash;
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String normalizeResultWireBody(String raw) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        for (String collection : new String[] {"teams", "players"}) {
            JsonArray rows = root.getAsJsonArray(collection);
            if (rows == null) continue;
            for (JsonElement row : rows) {
                JsonArray scores = row.getAsJsonObject().getAsJsonArray("gameScores");
                if (scores == null) continue;
                for (JsonElement score : scores) {
                    JsonObject value = score.getAsJsonObject();
                    JsonElement key = value.get("gameKey");
                    if (key != null && key.isJsonPrimitive()) {
                        GameTypeEnum game = GameTypeEnum.fromCommand(key.getAsString());
                        if (game != null) value.addProperty("gameKey", game.name());
                    }
                    // Version selection is owned by cc-web's event catalog.
                    value.remove("variantKey");
                }
            }
        }
        return new Gson().toJson(root);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme() != null
                && left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost() != null
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean isLoopback(String host) {
        return host != null
                && (host.equalsIgnoreCase("localhost")
                        || host.equals("127.0.0.1")
                        || host.equals("::1"));
    }
}
