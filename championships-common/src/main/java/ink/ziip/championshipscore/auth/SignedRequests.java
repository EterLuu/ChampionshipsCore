package ink.ziip.championshipscore.auth;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** cc-web signs the raw path without the query string and hashes the exact UTF-8 body. */
public final class SignedRequests {
    private final String keyId;
    private final SecretKeySpec key;

    public SignedRequests(String keyId, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("HMAC secret must contain at least 32 bytes");
        this.keyId = keyId;
        key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static URI baseUri(String url, boolean allowInsecurePrivateHttp) {
        URI base = URI.create(url);
        String scheme = base.getScheme();
        String host = base.getHost();
        if (host == null
                || base.getRawUserInfo() != null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
            throw new IllegalArgumentException("Expected an HTTP(S) URL with a host");
        if ("http".equalsIgnoreCase(scheme)
                && !allowInsecurePrivateHttp
                && !("localhost".equalsIgnoreCase(host)
                        || "127.0.0.1".equals(host)
                        || "[::1]".equals(host)))
            throw new IllegalArgumentException("Non-loopback HTTP requires explicit opt-in");
        return base;
    }

    public static String cursor(String value) {
        if (value == null || !value.matches("[0-9]{1,19}"))
            throw new IllegalArgumentException("Invalid bridge cursor");
        return value;
    }

    public HttpRequest get(URI uri, Duration timeout) throws GeneralSecurityException {
        return signed("GET", uri, timeout, "").GET().build();
    }

    public HttpRequest post(URI uri, Duration timeout, String body)
            throws GeneralSecurityException {
        return signed("POST", uri, timeout, body)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest.Builder signed(String method, URI uri, Duration timeout, String body)
            throws GeneralSecurityException {
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        String digest =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(body.getBytes(StandardCharsets.UTF_8)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        String canonical =
                String.join("\n", method, uri.getRawPath(), timestamp, requestId, digest);
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("X-CC-Key-Id", keyId)
                .header("X-CC-Timestamp", timestamp)
                .header("X-CC-Request-Id", requestId)
                .header(
                        "X-CC-Signature",
                        HexFormat.of()
                                .formatHex(
                                        mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
    }
}
