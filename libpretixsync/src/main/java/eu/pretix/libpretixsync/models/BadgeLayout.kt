package eu.pretix.libpretixsync.models

import org.json.JSONArray

data class BadgeLayout(
    val id: Long,
    val backgroundFilename: String?,
    val eventSlug: String,
    val isDefault: Boolean,
    val layout: JSONArray,
    val serverId: Long,
) {
    companion object {
        fun defaultWithLayout(layout: String): BadgeLayout {
            return BadgeLayout(
                id = 0L,
                backgroundFilename = null,
                eventSlug = "",
                isDefault = true,
                layout = JSONArray(layout),
                serverId = 0L,
            )
        }
    }
}

