package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.OrderPosition
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import org.json.JSONArray
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
        variationServerId = parseVariationId(json),
        attendeeNameParts = json.optJSONObject("attendee_name_parts"),
        city = json.optString("city", null),
        company = json.optString("company", null),
        country = json.optString("country", null),
        email = json.optString("email", null),
        street = json.optString("street", null),
        zipcode = json.optString("zipcode", null),
        price = parsePrice(json),
        taxRate = parseTaxRate(json),
        taxCode = parseTaxCode(json),
        taxValue = parseTaxValue(json),
        seatName = parseSeatName(json),
        addonToServerId = parseAddonToServerId(json),
        blocked = parseBlocked(json),
        validFrom = SafeOffsetDateTimeMapper.decode(json, "valid_from"),
        validUntil = SafeOffsetDateTimeMapper.decode(json, "valid_until"),
        answers = parseAnswers(json),
        answersWithOptionIds = parseAnswersWithOptionIds(json),
        attendeeEmail = this.attendee_email,
        attendeeName = this.attendee_name,
    )
}

private fun parseVariationId(json: JSONObject): Long? {
    try {
        val value: Long = json.optLong("variation", 0L)
        if (value == 0L) {
            return null
        }
        return value
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parsePrice(json: JSONObject): BigDecimal? {
    try {
        return BigDecimal(json.getString("price"))
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parseTaxCode(json: JSONObject): String? {
    try {
        if (json.isNull("tax_code")) {
            return null
        }
        return json.optString("tax_code", null)
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

private fun parseSeatName(json: JSONObject): String? {
    try {
        val seat = json.optJSONObject("seat")
        if (seat != null) {
            return seat.getString("name")
        }
    } catch (e: JSONException) {
    }
    return null
}

private fun parseAddonToServerId(json: JSONObject): Long? {
    try {
        val value = json.optLong("addon_to", 0L)
        if (value == 0L) {
            return null
        }
        return value
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

fun parseBlocked(json: JSONObject): Boolean {
    try {
        if (!json.has("blocked") || json.isNull("blocked")) {
            return false
        }
        return true
    } catch (e: JSONException) {
        e.printStackTrace()
        return false
    }
}

private fun parseAnswers(json: JSONObject): Map<Long, String>? {
    try {
        val arr: JSONArray = json.getJSONArray("answers")
        val res: MutableMap<Long, String> = HashMap()
        for (i in 0 until arr.length()) {
            res[arr.getJSONObject(i).getLong("question")] = arr.getJSONObject(i).getString("answer")
        }
        return res
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parseAnswersWithOptionIds(json: JSONObject): Map<Long, String>? {
    try {
        val arr: JSONArray = json.getJSONArray("answers")
        val res: MutableMap<Long, String> = HashMap()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            val opts = a.getJSONArray("options")
            if (opts.length() > 0) {
                val aw = StringBuilder()
                for (j in 0 until opts.length()) {
                    if (aw.length > 0) {
                        aw.append(",")
                    }
                    aw.append(opts.getLong(j))
                }
                res[a.getLong("question")] = aw.toString()
            } else {
                res[a.getLong("question")] = a.getString("answer")
            }
        }
        return res
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}
