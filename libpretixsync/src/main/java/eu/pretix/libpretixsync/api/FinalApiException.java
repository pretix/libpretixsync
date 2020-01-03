package eu.pretix.libpretixsync.api;

import org.json.JSONObject;

import java.io.IOException;

public class FinalApiException extends ApiException {
    public JSONObject data;

    public FinalApiException(String msg) {
        super(msg);
    }

    public FinalApiException(String msg, Exception e) {
        super(msg, e);
    }

    public FinalApiException(String msg, Exception e, JSONObject data) {
        super(msg, e);
        this.data = data;
    }
}
