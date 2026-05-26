package eu.pretix.libpretixsync.db

enum class ReusableMediaType(val serverName: String?) {
    NONE(null),
    BARCODE("barcode"),
    NFC_UID("nfc_uid"),

    NFC_MF0AES("nfc_mf0aes"),
    UNSUPPORTED(null);

    fun isNfcBased(): Boolean {
        return this.serverName?.startsWith("nfc_") ?: false;
    }

    companion object {
        private val map = ReusableMediaType.entries.associateBy(ReusableMediaType::serverName)
        fun getByServerName(serverName: String?) = map[serverName]
    }
}
