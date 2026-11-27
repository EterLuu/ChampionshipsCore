package ink.ziip.championshipscore.authbridge.bridge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import ink.ziip.championshipscore.auth.SignedRequests;
import ink.ziip.championshipscore.authbridge.model.BridgeChangeBatch;
import ink.ziip.championshipscore.authbridge.model.BridgeControlJobEnvelope;
import ink.ziip.championshipscore.authbridge.model.BridgeSnapshot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BridgeApiClient implements AutoCloseable {
    private static final String CHANGE_PATH = "/api/internal/bridge/changes";

    /** Changes requested per page; a shorter page means the writer has caught up. */
    public static final int CHANGE_PAGE_SIZE = 100;

    private final URI baseUri;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final SignedRequests requests;
    private final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public BridgeApiClient(
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

    public BridgeChangeBatch changesAfter(String cursor) throws Exception {
        try {
            SignedRequests.cursor(cursor);
        } catch (IllegalArgumentException malformed) {
            throw BridgeApiException.invalidLocalCursor();
        }
        URI uri = baseUri.resolve(CHANGE_PATH + "?after=" + cursor + "&limit=" + CHANGE_PAGE_SIZE);
        HttpRequest request = requests.get(uri, requestTimeout);
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isSuccess(response.statusCode()))
            throw new BridgeApiException(
                    "Bridge API", response.statusCode(), errorCode(response.body()));
        return mapper.readValue(response.body(), BridgeChangeBatch.class);
    }

    public BridgeSnapshot snapshot() throws Exception {
        String path = "/api/internal/bridge/snapshot";
        HttpRequest request = requests.get(baseUri.resolve(path), requestTimeout);
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isSuccess(response.statusCode())) {
            throw new IllegalStateException(
                    "Bridge snapshot API returned HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), BridgeSnapshot.class);
    }

    public void acknowledge(String cursor) throws Exception {
        String path = "/api/internal/bridge/ack";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("through", cursor);
        String body = mapper.writeValueAsString(payload);
        HttpRequest request = requests.post(baseUri.resolve(path), requestTimeout, body);
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isSuccess(response.statusCode()))
            throw new BridgeApiException(
                    "Bridge acknowledgment", response.statusCode(), errorCode(response.body()));
    }

    public BridgeControlJobEnvelope controlJob() throws Exception {
        String path = "/api/internal/bridge/control-job";
        HttpRequest request = requests.get(baseUri.resolve(path), requestTimeout);
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isSuccess(response.statusCode())) {
            throw new IllegalStateException(
                    "Bridge control API returned HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), BridgeControlJobEnvelope.class);
    }

    public void completeControlJob(
            String jobId, boolean success, Map<String, Object> result, String error)
            throws Exception {
        ink.ziip.championshipscore.auth.AuthIdentity.parseUuid(jobId, "control job id");
        String path = "/api/internal/bridge/control-jobs/" + jobId + "/complete";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        if (result != null) payload.put("result", result);
        if (error != null && !error.isBlank())
            payload.put("error", error.length() > 1000 ? error.substring(0, 1000) : error);
        String body = mapper.writeValueAsString(payload);
        HttpRequest request = requests.post(baseUri.resolve(path), requestTimeout, body);
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (!isSuccess(response.statusCode())) {
            throw new IllegalStateException(
                    "Bridge control completion returned HTTP " + response.statusCode());
        }
    }

    /** cc-web reports failures as {@code {"message": "STABLE_CODE", ...}}. */
    private String errorCode(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            String message = mapper.readTree(body).path("message").asText(null);
            return message != null && message.matches("[A-Z][A-Z0-9_]{1,63}") ? message : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isSuccess(int statusCode) {
        // NestJS @Post endpoints return 201 Created by default; treat any 2xx as success.
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public void close() {
        client.shutdownNow();
    }
}
