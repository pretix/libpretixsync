package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.Event
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import eu.pretix.libpretixsync.utils.I18nString
import org.json.JSONException
import org.json.JSONObject
import java.time.ZoneId
import eu.pretix.libpretixsync.models.Event as EventModel

fun Event.toModel(): EventModel {
    val json = JSONObject(this.json_data!!)

    return EventModel(
        id = this.id,
        name = getName(json),
        slug = this.slug!!,
        currency = this.currency!!,
        isLive = this.live,
        hasSubEvents = this.has_subevents,
        // Use date values from JSON, as they contain time zone information
        dateFrom = SafeOffsetDateTimeMapper.decode(json, "date_from")!!,
        dateTo = SafeOffsetDateTimeMapper.decode(json, "date_to"),
        timezone = getTimezone(json),
        plugins = parsePlugins(json),
        hasSeating = parseHasSeating(json),
        seatCategoryMapping = json.getJSONObject("seat_category_mapping"),
    )
}

private fun getName(json: JSONObject): String =
    try {
        I18nString.toString(json.getJSONObject("name"))
    } catch (e: JSONException) {
        e.printStackTrace()
        ""
    }

private fun getTimezone(json: JSONObject): ZoneId =
    try {
        ZoneId.of(json.optString("timezone", "UTC"))
    } catch (e: JSONException) {
        ZoneId.of("UTC")
    }

private fun parsePlugins(json: JSONObject): List<String> {
    try {
        val plugins = json.optJSONArray("plugins")
        if (plugins == null || plugins.length() == 0) {
            return emptyList()
        }

        val res = mutableListOf<String>()
        for (i in 0 until plugins.length()) {
            res.add(plugins.getString(i))
        }
        return res
    } catch (e: JSONException) {
        return emptyList()
    }
}

private fun parseHasSeating(json: JSONObject): Boolean {
    return try {
        !json.isNull("seating_plan")
    } catch (e: JSONException) {
        false
    }
}
