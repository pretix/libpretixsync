package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.Orders
import eu.pretix.libpretixsync.models.Order
import org.json.JSONException
import org.json.JSONObject

fun Orders.toModel(): Order {
    val json = JSONObject(this.json_data!!)

    return Order(
        id = this.id,
        eventSlug = this.event_slug!!,
        code = this.code,
        checkInText = this.checkin_text,
        requiresCheckInAttention = this.checkin_attention,
        status = Order.Status.fromValue(this.status!!),
        testMode = parseTestMode(json),
        email = this.email,
    )
}

private fun parseTestMode(json: JSONObject): Boolean {
    try {
        return json.getBoolean("testmode")
    } catch (e: JSONException) {
        e.printStackTrace()
        return false
    }
}
