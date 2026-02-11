package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.models.Order
import eu.pretix.libpretixsync.sqldelight.Orders
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal

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
        requiresApproval = parseRequiresApproval(json),
        validIfPending = this.valid_if_pending ?: false,
        total = parseTotal(json),
        pendingTotal = parsePendingTotal(json),
        payments = parsePayments(json),
        refunds = parseRefunds(json),
        locale = parseLocale(json),
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

private fun parseRequiresApproval(json: JSONObject): Boolean {
    try {
        return json.getBoolean("require_approval")
    } catch (e: JSONException) {
        e.printStackTrace()
        return false
    }
}

private fun parseTotal(json: JSONObject): BigDecimal? {
    try {
        return BigDecimal(json.getString("total"))
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parsePendingTotal(json: JSONObject): BigDecimal? {
    try {
        var total = BigDecimal(json.getString("total"))
        if (json.getString("status") == "c") {
            total = BigDecimal.ZERO
        }

        var paymentSum = BigDecimal.ZERO
        val payments = json.getJSONArray("payments")
        for (i in 0 until payments.length()) {
            val payment = payments.getJSONObject(i)
            if (payment.getString("state").matches("^(confirmed|refunded)$".toRegex())) {
                paymentSum = paymentSum.add(BigDecimal(payment.getString("amount")))
            }
        }

        var refundSum = BigDecimal.ZERO
        val refunds = json.getJSONArray("refunds")
        for (i in 0 until refunds.length()) {
            val refund = refunds.getJSONObject(i)
            if (refund.getString("state").matches("^(done|transit|created)$".toRegex())) {
                refundSum = refundSum.add(BigDecimal(refund.getString("amount")))
            }
        }
        return total.subtract(paymentSum).add(refundSum)
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parsePayments(json: JSONObject): JSONArray {
    try {
        return json.getJSONArray("payments")
    } catch (e: JSONException) {
        e.printStackTrace()
        return JSONArray()
    }
}

private fun parseRefunds(json: JSONObject): JSONArray {
    try {
        return json.getJSONArray("refunds")
    } catch (e: JSONException) {
        e.printStackTrace()
        return JSONArray()
    }
}

private fun parseLocale(json: JSONObject): String? {
    try {
        return json.getString("locale")
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}
