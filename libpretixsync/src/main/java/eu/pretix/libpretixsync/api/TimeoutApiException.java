package eu.pretix.libpretixsync.api;

import org.json.JSONObject;

public class TimeoutApiException extends ApiException {
    public TimeoutApiException(String msg) {
        super(msg);
    }

    public TimeoutApiException(String msg, Exception e) {
        super(msg, e);
    }
}
