package ink.ziip.championshipscore.authbridge.bridge;

/**
 * A non-success cc-web Bridge API response. Keeps the HTTP status and cc-web's stable error code so
 * the synchronizer can tell a rejected cursor apart from an outage or a credential problem.
 */
public final class BridgeApiException extends IllegalStateException {
    private final int status;
    private final String code;

    public BridgeApiException(String operation, int status, String code) {
        super(operation + " returned HTTP " + status + (code == null ? "" : " (" + code + ")"));
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    /** cc-web's error code, or {@code null} when the response carried none. */
    public String code() {
        return code;
    }

    /**
     * cc-web no longer accepts this writer's cursor: it is ahead of every outbox id cc-web has
     * issued (the website database was restored from an older backup) or it is malformed.
     */
    public boolean rejectsCursor() {
        return (status == 409 && "CURSOR_AHEAD_OF_OUTBOX".equals(code))
                || (status == 400 && "INVALID_CURSOR".equals(code));
    }
}
