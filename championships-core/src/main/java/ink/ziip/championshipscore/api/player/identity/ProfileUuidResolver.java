package ink.ziip.championshipscore.api.player.identity;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the standard Yggdrasil/Mojang name-profile response without creating a login session. */
public final class ProfileUuidResolver {
    private static final Pattern PROFILE_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F-]{32,36})\\\"");
    private static final Pattern PROFILE_NAME = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([A-Za-z0-9_]{3,16})\\\"");
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private final HttpClient client;
    private final Duration requestTimeout;

    public ProfileUuidResolver(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    public @NotNull UUID resolve(@NotNull String baseUrl, @NotNull String username)
            throws PlayerUuidLookupException {
        if (!username.matches("[A-Za-z0-9_]{3,16}")) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.INVALID_USERNAME,
                    "Invalid Minecraft username: " + username);
        }
        final URI base;
        try {
            if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("blank URL");
            base = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        } catch (RuntimeException exception) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.CONFIGURATION,
                    "Invalid profile API base URL", exception);
        }
        if (!"http".equalsIgnoreCase(base.getScheme()) && !"https".equalsIgnoreCase(base.getScheme())) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.CONFIGURATION,
                    "Profile API URL must use HTTP or HTTPS");
        }
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(base + "/users/profiles/minecraft/" + username))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        final HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.SERVICE_UNAVAILABLE,
                    "Profile API request was interrupted", exception);
        } catch (IOException exception) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.SERVICE_UNAVAILABLE,
                    "Profile API is unavailable", exception);
        }
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            closeQuietly(response.body());
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.PLAYER_NOT_FOUND,
                    "Profile API has no player named " + username);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.SERVICE_UNAVAILABLE,
                    "Profile API returned HTTP " + response.statusCode());
        }
        final byte[] body;
        try (InputStream input = response.body()) {
            body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        } catch (IOException exception) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.SERVICE_UNAVAILABLE,
                    "Unable to read profile API response", exception);
        }
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.INVALID_RESPONSE,
                    "Profile API response is too large");
        }
        String json = new String(body, StandardCharsets.UTF_8);
        Matcher idMatcher = PROFILE_ID.matcher(json);
        Matcher nameMatcher = PROFILE_NAME.matcher(json);
        if (!idMatcher.find() || !nameMatcher.find()) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.INVALID_RESPONSE,
                    "Profile API response is missing a valid UUID or player name");
        }
        if (!username.equalsIgnoreCase(nameMatcher.group(1))) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.INVALID_RESPONSE,
                    "Profile API returned a different player name");
        }
        try {
            return parseUuid(idMatcher.group(1));
        } catch (IllegalArgumentException exception) {
            throw new PlayerUuidLookupException(PlayerUuidLookupException.Reason.INVALID_RESPONSE,
                    "Profile API returned an invalid UUID", exception);
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    static @NotNull UUID parseUuid(@NotNull String value) {
        String compact = value.replace("-", "");
        if (!compact.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("Invalid profile UUID");
        return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20));
    }
}
