package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.ItemCategory
import eu.pretix.libpretixsync.utils.I18nString
import org.json.JSONException
import org.json.JSONObject
import eu.pretix.libpretixsync.models.ItemCategory as ItemCategoryModel

fun ItemCategory.toModel(): ItemCategoryModel {
    val json = JSONObject(this.json_data!!)

    return ItemCategoryModel(
        id = this.id,
        serverId = this.server_id!!,
        eventSlug = this.event_slug!!,
        isAddOn = this.is_addon,
        position = this.position!!,
        name = parseName(json),
        nameI18n = json.getJSONObject("name"),
        description = parseDescription(json),
        descriptionI18n = json.optJSONObject("description") ?: null,
    )
}

private fun parseName(json: JSONObject): String {
    return try {
        I18nString.toString(json.getJSONObject("name"))
    } catch (e: JSONException) {
        e.printStackTrace()
        ""
    }
}

private fun parseDescription(json: JSONObject): String? {
    return try {
        if (!json.isNull("description")) {
            I18nString.toString(json.getJSONObject("description")) ?: ""
        } else {
            null
        }
    } catch (e: JSONException) {
        e.printStackTrace()
        null
    }
}
