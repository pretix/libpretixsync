package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.models.Discount.DiscountSubeventMode
import eu.pretix.libpretixsync.sqldelight.Discount
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import eu.pretix.libpretixsync.models.Discount as DiscountModel

fun Discount.toModel(): DiscountModel {
    val json = JSONObject(this.json_data)

    return DiscountModel(
        id = this.id,
        serverId = this.server_id,
        eventSlug = this.event_slug,
        active = this.active,
        position = this.position,
        allSalesChannels = json.optBoolean("all_sales_channels", false),
        limitSalesChannels = if (json.has("limit_sales_channels")) {
            jsonArrayToStringList(json.getJSONArray("limit_sales_channels"))
        } else if (json.has("sales_channels")) {  // legacy pretix
            jsonArrayToStringList(json.getJSONArray("sales_channels"))
        } else {
            emptyList()
        },
        availableFrom = SafeOffsetDateTimeMapper.decode(json, "available_from"),
        availableUntil = SafeOffsetDateTimeMapper.decode(json, "available_until"),
        subeventMode = when (json.getString("subevent_mode")) {
            "mixed" -> DiscountSubeventMode.MIXED
            "same" -> DiscountSubeventMode.SAME
            "distinct" -> DiscountSubeventMode.DISTINCT
            else -> DiscountSubeventMode.MIXED
        },
        subeventDateFrom = SafeOffsetDateTimeMapper.decode(json, "subevent_date_from"),
        subeventDateUntil = SafeOffsetDateTimeMapper.decode(json, "subevent_date_until"),
        conditionAllProducts = json.getBoolean("condition_all_products"),
        conditionLimitProducts = jsonArrayToLongList(json.getJSONArray("condition_limit_products")),
        conditionApplyToAddons = json.getBoolean("condition_apply_to_addons"),
        conditionIgnoreVoucherDiscounted = json.getBoolean("condition_ignore_voucher_discounted"),
        conditionMinCount = json.getInt("condition_min_count"),
        conditionMinValue = BigDecimal(json.getString("condition_min_value")),
        benefitSameProducts = json.optBoolean("benefit_same_products", true),
        benefitLimitProducts = jsonArrayToLongList(json.optJSONArray("benefit_limit_products")),
        benefitDiscountMatchingPercent = BigDecimal(json.getString("benefit_discount_matching_percent")),
        benefitOnlyApplyToCheapestNMatches = if (json.isNull("benefit_only_apply_to_cheapest_n_matches"))
            null
        else
            json.getInt("benefit_only_apply_to_cheapest_n_matches"),
        benefitApplyToAddons = json.optBoolean("benefit_apply_to_addons", false),
        benefitIgnoreVoucherDiscounted = json.optBoolean("benefit_ignore_voucher_discounted", true),
    )
}

private fun jsonArrayToLongList(jsonArray: JSONArray?): List<Long> {
    if (jsonArray == null) return emptyList()
    return (0 until jsonArray.length()).map {
        jsonArray.getLong(it)
    }
}

private fun jsonArrayToStringList(jsonArray: JSONArray?): List<String> {
    if (jsonArray == null) return emptyList()
    return (0 until jsonArray.length()).map {
        jsonArray.getString(it)
    }
}
