package eu.pretix.libpretixsync.utils

import eu.pretix.libpretixsync.db.ReusableMediaType

fun getActiveMediaTypes(
    settingsManager: SettingsManager,
    eventSlug: String?
): List<ReusableMediaType> {
    if (eventSlug.isNullOrBlank()) {
        return emptyList()
    }

    val settings = settingsManager.getBySlug(eventSlug)
    if (settings == null) {
        return emptyList()
    }
    val l = mutableListOf<ReusableMediaType>()

    if (settings.json.optBoolean("reusable_media_type_barcode")) {
        l.add(ReusableMediaType.BARCODE)
    }
    if (settings.json.optBoolean("reusable_media_type_nfc_uid")) {
        l.add(ReusableMediaType.NFC_UID)
    } else {
        if (settings.json.optBoolean("reusable_media_type_nfc_mf0aes")) {
            l.add(ReusableMediaType.NFC_MF0AES)
        }
    }
    return l
}
