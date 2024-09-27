package eu.pretix.libpretixsync.models

import org.json.JSONObject

data class CheckInList(
    val id: Long,
    val serverId: Long,
    val allItems: Boolean,
    val eventSlug: String?,
    val includePending: Boolean,
    val name: String?,
    val subEventId: Long?,
    val allowMultipleEntries: Boolean,
    val allowEntryAfterExit: Boolean,
    val addonMatch: Boolean,
    val rules: JSONObject?,
)
