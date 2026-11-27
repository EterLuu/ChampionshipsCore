package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.auth.SignedRequests;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Signs and submits leaderboard snapshots with the same HMAC contract as AuthBridge. */
public final class WebLeaderboardApiClient implements AutoCloseable {
    private static final String PATH = "/api/internal/bridge/leaderboards";

    private final URI baseUri;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final SignedRequests requests;

    public WebLeaderboardApiClient(
            @NotNull String baseUrl,
            @NotNull String keyId,
            @NotNull String secret,
            boolean allowInsecurePrivateHttp,
            long connectTimeoutSeconds,
            long requestTimeoutSeconds) {
        this.baseUri = SignedRequests.baseUri(baseUrl, allowInsecurePrivateHttp);
        this.requests = new SignedRequests(keyId, secret);
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build();
    }

    public void submit(@NotNull WebLeaderboardSnapshot snapshot) throws Exception {
        String body = toJson(snapshot);
        HttpRequest request = requests.post(baseUri.resolve(PATH), requestTimeout, body);
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException(
                    "Leaderboard API returned HTTP " + response.statusCode());
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
                json.append(",\"value\":")
                        .append(entry.value())
                        .append(",\"tieDurationMs\":")
                        .append(entry.tieDurationMs())
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

    @Override
    public void close() {
        client.shutdownNow();
    }
}
