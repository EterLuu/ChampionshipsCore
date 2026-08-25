package ink.ziip.championshipscore.authbridge.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Resolves the UUID source explicitly declared by cc-web for a Bridge payload. */
public final class BridgeUuidResolver {
    private static final String MOJANG_PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private final HttpClient client;
    private final Duration requestTimeout;
    private final String mojangProfileBaseUrl;
    private final ObjectMapper mapper;

    public BridgeUuidResolver(Duration connectTimeout, Duration requestTimeout) {
        this(connectTimeout, requestTimeout, MOJANG_PROFILE_URL);
    }

    BridgeUuidResolver(Duration connectTimeout, Duration requestTimeout, String mojangProfileBaseUrl) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
        this.mojangProfileBaseUrl = mojangProfileBaseUrl.endsWith("/")
                ? mojangProfileBaseUrl : mojangProfileBaseUrl + "/";
        this.mapper = new ObjectMapper();
    }

    public UUID resolve(String username, String source, String suppliedUuid) {
        requireUsername(username);
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Bridge response omitted uuidSource for " + username);
        }
        return switch (source.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "UUID" -> parseUuid(suppliedUuid, "minecraftUuid");
            case "OFFLINE" -> offlineUuid(username);
            case "ONLINE" -> resolveMojang(username);
            default -> throw new IllegalArgumentException("Unsupported uuidSource: " + source);
        };
    }

    static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field);
        String compact;
        if (value.matches("[0-9a-fA-F]{32}")) {
            compact = value;
        } else if (value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            compact = value.replace("-", "");
        } else {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20));
    }

    private UUID resolveMojang(String username) {
        final HttpResponse<InputStream> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(mojangProfileBaseUrl + username))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mojang profile lookup was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Mojang profile lookup is unavailable", exception);
        }
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            closeQuietly(response.body());
            throw new IllegalArgumentException("Mojang profile not found for " + username);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            throw new IllegalStateException("Mojang profile returned HTTP " + response.statusCode());
        }
        final byte[] body;
        try (InputStream input = response.body()) {
            body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Mojang profile response", exception);
        }
        if (body.length > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Mojang profile response is too large");
        try {
            JsonNode profile = mapper.readTree(body);
            String name = profile.path("name").asText("");
            String id = profile.path("id").asText("");
            if (!username.equalsIgnoreCase(name)) throw new IllegalArgumentException("Mojang returned a different player name");
            return parseUuid(id, "Mojang profile id");
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Invalid Mojang profile response", exception);
        }
    }

    private static void requireUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Invalid Minecraft username");
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }
}
