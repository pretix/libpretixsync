package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.Quota
import eu.pretix.libpretixsync.models.Quota as QuotaModel
import org.json.JSONException
import org.json.JSONObject

fun Quota.toModel(): QuotaModel {
    val json = JSONObject(this.json_data!!)

    return QuotaModel(
        id = this.id,
        serverId = this.server_id!!,
        available = this.available!!,
        availableNumber = this.available_number,
        size = this.size,
        eventSlug = this.event_slug,
        subEventServerId = this.subevent_id,
        items = parseItems(json),
        variations = parseVariations(json),
        isUnlimited = parseIsUnlimited(json),
    )
}

private fun parseItems(json: JSONObject): List<Long> {
    val items = json.getJSONArray("items")
    val res = mutableListOf<Long>()
    for (i in 0 until items.length()) {
        res.add(items.getLong(i))
    }
    return res
}

private fun parseVariations(json: JSONObject): List<Long> {
    val items = json.getJSONArray("variations")
    val res = mutableListOf<Long>()
    for (i in 0 until items.length()) {
        res.add(items.getLong(i))
    }
    return res
}

private fun parseIsUnlimited(json: JSONObject): Boolean {
    return try {
        json.isNull("size")
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}
