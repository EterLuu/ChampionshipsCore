package ink.ziip.championshipscore.api.test.auth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ink.ziip.championshipscore.auth.SignedRequests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Calls the cc-web TEST_HARNESS API without accepting UUIDs from the Bot client. */
public final class TestBotIdentityClient implements AutoCloseable {
    private static final String PATH = "/api/internal/test/bots/leases";
    private final URI baseUri;
    private final HttpClient client;
    private final SignedRequests requests;
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
        this.baseUri = SignedRequests.baseUri(baseUrl, allowInsecurePrivateHttp);
        this.requests = new SignedRequests(keyId, secret);
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
        HttpRequest request = requests.post(baseUri.resolve(PATH), timeout, body);
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

    public record Lease(String username, String token) {}

    @Override
    public void close() {
        client.shutdownNow();
    }
}
