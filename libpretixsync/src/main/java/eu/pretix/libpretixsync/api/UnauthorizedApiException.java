package eu.pretix.libpretixsync.api;

public class UnauthorizedApiException extends FinalApiException {

    public UnauthorizedApiException(String msg) {
        super(msg);
    }

}
