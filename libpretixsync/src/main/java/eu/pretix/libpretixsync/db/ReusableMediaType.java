package eu.pretix.libpretixsync.db;

public enum ReusableMediaType {
    NONE(null),
    BARCODE("barcode"),
    NFC_UID("nfc_uid"),

    NFC_MF0AES("nfc_mf0aes"),
    UNSUPPORTED(null);

    public final String serverName;

    private ReusableMediaType(String serverName) {
        this.serverName = serverName;
    }
}
