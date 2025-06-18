package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.CheckInList
import org.json.JSONException
import org.json.JSONObject
import eu.pretix.libpretixsync.models.CheckInList as CheckInListModel

fun CheckInList.toModel(): CheckInListModel {
    val json = JSONObject(this.json_data!!)

    return CheckInListModel(
        id = this.id,
        serverId = this.server_id!!,
        allItems = this.all_items,
        eventSlug = this.event_slug,
        includePending = this.include_pending,
        name = this.name,
        subEventId = this.subevent_id,
        allowMultipleEntries = parseAllowMultipleEntries(json),
        allowEntryAfterExit = parseAllowEntryAfterExit(json),
        addonMatch = parseAddonMatch(json),
        rules = parseRules(json),
    )
}

fun parseAllowMultipleEntries(json: JSONObject): Boolean {
    return try {
        json.getBoolean("allow_multiple_entries")
    } catch (e: JSONException) {
        false
    }
}

fun parseAllowEntryAfterExit(json: JSONObject): Boolean {
    return try {
        json.getBoolean("allow_entry_after_exit")
    } catch (e: JSONException) {
        false
    }
}

fun parseAddonMatch(json: JSONObject): Boolean {
    return try {
        json.optBoolean("addon_match", false)
    } catch (e: JSONException) {
        false
    }
}

private fun parseRules(json: JSONObject): JSONObject? {
    return try {
        json.optJSONObject("rules")
    } catch (e: JSONException) {
        null
    }
}
