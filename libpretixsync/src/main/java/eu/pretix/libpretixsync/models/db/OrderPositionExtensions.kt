package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.OrderPosition
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import eu.pretix.libpretixsync.models.OrderPosition as OrderPositionModel

fun OrderPosition.toModel(): OrderPositionModel {
    val json = JSONObject(this.json_data)

    return OrderPositionModel(
        id = this.id,
        serverId = this.server_id,
        itemId = this.item!!,
        orderId = this.order_ref!!,
        positionId = this.positionid!!,
        secret = this.secret,
        subEventServerId = this.subevent_id,
        variationServerId = this.variation_id,
        attendeeNameParts = json.optJSONObject("attendee_name_parts"),
        city = json.optString("city", null),
        company = json.optString("company", null),
        country = json.optString("country", null),
        email = json.optString("email", null),
        street = json.optString("street", null),
        zipcode = json.optString("zipcode", null),
        price = parsePrice(json),
        taxRate = parseTaxRate(json),
        taxValue = parseTaxValue(json),
        attendeeEmail = this.attendee_email,
        attendeeName = this.attendee_name,
    )
}

private fun parsePrice(json: JSONObject): BigDecimal? {
    try {
        return BigDecimal(json.getString("price"))
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parseTaxRate(json: JSONObject): BigDecimal? {
    try {
        return BigDecimal(json.getString("tax_rate"))
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parseTaxValue(json: JSONObject): BigDecimal? {
    try {
        return BigDecimal(json.getString("tax_value"))
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}
