package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.models.Order
import eu.pretix.libpretixsync.sqldelight.Orders
import eu.pretix.libpretixsync.utils.JSONUtils
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
        salesChannel = JSONUtils.optString(json, "sales_channel"),
        phone = JSONUtils.optString(json, "phone"),
        invoiceAddress = parseInvoiceAddress(json),
        invoiceAddressNameParts = parseInvoiceAddressNameParts(json),
        answersByIdentifier = parseAnswersByIdentifier(json),
        answersByIdentifierWithOptionIdentifiers = parseAnswersByIdentifierWithOptionIdentifiers(json),
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
        if (!json.has("payments") || !json.has("refunds")) { return null }
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
        if (!json.has("payments")) { return JSONArray() }
        return json.getJSONArray("payments")
    } catch (e: JSONException) {
        e.printStackTrace()
        return JSONArray()
    }
}

private fun parseRefunds(json: JSONObject): JSONArray {
    try {
        if (!json.has("refunds")) { return JSONArray() }
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

private fun parseAnswersByIdentifier(json: JSONObject): Map<String, String>? {
    try {
        val arr: JSONArray = json.getJSONArray("answers")
        val res: MutableMap<String, String> = HashMap()
        for (i in 0 until arr.length()) {
            res[arr.getJSONObject(i).getString("datafield")] = arr.getJSONObject(i).getString("answer")
        }
        return res
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parseAnswersByIdentifierWithOptionIdentifiers(json: JSONObject): Map<String, String>? {
    try {
        val arr: JSONArray = json.getJSONArray("answers")
        val res: MutableMap<String, String> = HashMap()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            val opts = a.optJSONArray("option_identifiers")
            if (opts != null && opts.length() > 0) {
                val aw = StringBuilder()
                for (j in 0 until opts.length()) {
                    if (aw.length > 0) {
                        aw.append(",")
                    }
                    aw.append(opts.getString(j))
                }
                res[a.getString("datafield")] = aw.toString()
            } else {
                res[a.getString("datafield")] = a.getString("answer")
            }
        }
        return res
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parseInvoiceAddressNameParts(json: JSONObject): JSONObject? {
    try {
        if (json.isNull("invoice_address")) return null
        return json.getJSONObject("invoice_address").getJSONObject("name_parts")
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}

private fun parseInvoiceAddress(json: JSONObject): JSONObject? {
    try {
        if (json.isNull("invoice_address")) return null
        return json.getJSONObject("invoice_address")
    } catch (e: JSONException) {
        e.printStackTrace()
        return null
    }
}
