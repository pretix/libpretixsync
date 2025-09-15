package eu.pretix.libpretixsync.models

import org.json.JSONObject

data class ItemCategory(
    val id: Long,
    val serverId: Long,
    val eventSlug: String,
    val isAddOn: Boolean,
    val position: Long,
    val name: String = "",
    val nameI18n: JSONObject = JSONObject(),
    val description: String? = null,
    val descriptionI18n: JSONObject? = null,
)
