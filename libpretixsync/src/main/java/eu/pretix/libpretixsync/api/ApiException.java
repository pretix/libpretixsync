package eu.pretix.libpretixsync.api;

import java.io.IOException;

public class ApiException extends IOException {

    public ApiException(String msg) {
        super(msg);
    }

    public ApiException(String msg, Exception e) {
        super(msg, e);
    }

}
