package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.TaxRule
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import eu.pretix.libpretixsync.models.TaxRule as TaxRuleModel

fun TaxRule.toModel(): TaxRuleModel {
    val json = JSONObject(this.json_data!!)

    return TaxRuleModel(
        id = this.id,
        serverId = this.server_id!!,
        rate = parseRate(json),
        includesTax = parseIncludesTax(json),
    )
}

private fun parseRate(json: JSONObject): BigDecimal {
    try {
        return BigDecimal(json.getString("rate"))
    } catch (e: JSONException) {
        e.printStackTrace()
        return BigDecimal(0.00)
    }
}

private fun parseIncludesTax(json: JSONObject): Boolean {
    try {
        return json.getBoolean("price_includes_tax")
    } catch (e: JSONException) {
        e.printStackTrace()
        return false
    }
}
