package eu.pretix.libpretixsync.api;

public class PermissionDeniedApiException extends FinalApiException {
    public String eventSlug;

    public PermissionDeniedApiException(String msg) {
        super(msg);
    }

    public PermissionDeniedApiException(String msg, Exception e) {
        super(msg, e);
    }

    public PermissionDeniedApiException(String msg, String eventSlug) {
        super(msg);
        this.eventSlug = eventSlug;
    }

    public PermissionDeniedApiException(String msg, Exception e, String eventSlug) {
        super(msg, e);
        this.eventSlug = eventSlug;
    }
}
