package eu.pretix.libpretixsync.api;

import org.json.JSONObject;

public class NotFoundApiException extends FinalApiException {

    public NotFoundApiException(String msg) {
        super(msg);
    }

    public NotFoundApiException(String msg, Exception e) {
        super(msg, e);
    }

    public NotFoundApiException(String msg, Exception e, JSONObject data) {
        super(msg, e, data);
    }
}
