package eu.pretix.libpretixsync.db;

public enum ReusableMediaType {
    NONE(null),
    BARCODE("barcode"),
    NFC_UID("nfc_uid"),
    NTAG_PRETIX1("ntag_pretix1"),
    UNSUPPORTED(null);

    public final String serverName;

    private ReusableMediaType(String serverName) {
        this.serverName = serverName;
    }
}
