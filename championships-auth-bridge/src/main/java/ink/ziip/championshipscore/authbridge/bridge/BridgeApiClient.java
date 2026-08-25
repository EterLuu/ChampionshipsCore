package ink.ziip.championshipscore.authbridge.bridge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.ziip.championshipscore.authbridge.model.BridgeChangeBatch;
import ink.ziip.championshipscore.authbridge.model.BridgeControlJobEnvelope;
import ink.ziip.championshipscore.authbridge.model.BridgeSnapshot;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BridgeApiClient {
    private static final String CHANGE_PATH = "/api/internal/bridge/changes";

    private final URI baseUri;
    private final String keyId;
    private final byte[] secret;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public BridgeApiClient(String baseUrl, String keyId, String secret, boolean allowInsecurePrivateHttp, Duration connectTimeout, Duration requestTimeout) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String scheme = this.baseUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Bridge API URL must use HTTP or HTTPS");
        }
        if ("http".equalsIgnoreCase(scheme) && !isLoopback(this.baseUri.getHost()) && !allowInsecurePrivateHttp) {
            throw new IllegalArgumentException("Non-loopback HTTP requires api.allow-insecure-private-http=true");
        }
        this.keyId = keyId;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        if (this.secret.length < 32) throw new IllegalArgumentException("Bridge HMAC secret must contain at least 32 bytes");
    }

    public BridgeChangeBatch changesAfter(String cursor) throws Exception {
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        String signature = sign("GET", CHANGE_PATH, timestamp, requestId, "");
        URI uri = baseUri.resolve(CHANGE_PATH + "?after=" + cursor + "&limit=100");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Accept", "application/json")
            .header("X-CC-Key-Id", keyId)
            .header("X-CC-Timestamp", timestamp)
            .header("X-CC-Request-Id", requestId)
            .header("X-CC-Signature", signature)
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isSuccess(response.statusCode())) throw new IllegalStateException("Bridge API returned HTTP " + response.statusCode());
        return mapper.readValue(response.body(), BridgeChangeBatch.class);
    }

    public BridgeSnapshot snapshot() throws Exception {
        String path = "/api/internal/bridge/snapshot";
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("X-CC-Key-Id", keyId)
                .header("X-CC-Timestamp", timestamp)
                .header("X-CC-Request-Id", requestId)
                .header("X-CC-Signature", sign("GET", path, timestamp, requestId, ""))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isSuccess(response.statusCode())) {
            throw new IllegalStateException("Bridge snapshot API returned HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), BridgeSnapshot.class);
    }

    public void acknowledge(String cursor) throws Exception {
        acknowledge(cursor, java.util.List.of());
    }

    public void acknowledge(String cursor, java.util.List<LocalAccessState.ServerUuidReport> serverUuids) throws Exception {
        String path = "/api/internal/bridge/ack";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("through", cursor);
        if (!serverUuids.isEmpty()) {
            payload.put("serverUuids", serverUuids.stream().map(report -> Map.of(
                    "accountId", report.accountId(), "serverUuid", report.serverUuid())).toList());
        }
        String body = mapper.writeValueAsString(payload);
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .header("X-CC-Key-Id", keyId)
            .header("X-CC-Timestamp", timestamp)
            .header("X-CC-Request-Id", requestId)
            .header("X-CC-Signature", sign("POST", path, timestamp, requestId, body))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (!isSuccess(response.statusCode())) throw new IllegalStateException("Bridge acknowledgment returned HTTP " + response.statusCode());
    }

    public BridgeControlJobEnvelope controlJob() throws Exception {
        String path = "/api/internal/bridge/control-job";
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("X-CC-Key-Id", keyId)
                .header("X-CC-Timestamp", timestamp)
                .header("X-CC-Request-Id", requestId)
                .header("X-CC-Signature", sign("GET", path, timestamp, requestId, ""))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isSuccess(response.statusCode())) {
            throw new IllegalStateException("Bridge control API returned HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), BridgeControlJobEnvelope.class);
    }

    public void completeControlJob(String jobId, boolean success,
                                   Map<String, Object> result, String error) throws Exception {
        String path = "/api/internal/bridge/control-jobs/" + jobId + "/complete";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        if (result != null) payload.put("result", result);
        if (error != null && !error.isBlank()) payload.put("error", error.length() > 1000 ? error.substring(0, 1000) : error);
        String body = mapper.writeValueAsString(payload);
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("X-CC-Key-Id", keyId)
                .header("X-CC-Timestamp", timestamp)
                .header("X-CC-Request-Id", requestId)
                .header("X-CC-Signature", sign("POST", path, timestamp, requestId, body))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (!isSuccess(response.statusCode())) {
            throw new IllegalStateException("Bridge control completion returned HTTP " + response.statusCode());
        }
    }

    private static boolean isSuccess(int statusCode) {
        // NestJS @Post endpoints return 201 Created by default; treat any 2xx as success.
        return statusCode >= 200 && statusCode < 300;
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
}
