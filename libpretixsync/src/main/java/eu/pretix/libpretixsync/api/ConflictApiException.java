package eu.pretix.libpretixsync.api;

import org.json.JSONObject;

public class ConflictApiException extends ApiException {
    public JSONObject data;

    public ConflictApiException(String msg) {
        super(msg);
    }

    public ConflictApiException(String msg, Exception e) {
        super(msg, e);
    }

    public ConflictApiException(String msg, Exception e, JSONObject data) {
        super(msg, e);
        this.data = data;
    }
}
