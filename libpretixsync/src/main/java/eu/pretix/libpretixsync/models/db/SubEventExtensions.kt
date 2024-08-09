package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import eu.pretix.libpretixsync.sqldelight.SubEvent
import eu.pretix.libpretixsync.utils.I18nString
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import eu.pretix.libpretixsync.models.SubEvent as SubEventModel

fun SubEvent.toModel(): SubEventModel {
    val json = JSONObject(this.json_data!!)

    return SubEventModel(
        id = this.id,
        name = parseName(json),
        // Use date values from JSON, as they contain time zone information
        dateFrom = SafeOffsetDateTimeMapper.decode(json, "date_from")!!,
        dateTo = SafeOffsetDateTimeMapper.decode(json, "date_to"),
        itemPriceOverrides = parseItemPriceOverrides(json),
        variationPriceOverrides = parseVariationPriceOverrides(json),
        hasSeating = parseHasSeating(json),
        seatCategoryMapping = json.getJSONObject("seat_category_mapping"),
    )
}

private fun parseName(json: JSONObject): String {
    return try {
        I18nString.toString(json.getJSONObject("name"))
    } catch (e: JSONException) {
        e.printStackTrace()
        ""
    }
}

private fun parseItemPriceOverrides(json: JSONObject) =
    json.getJSONArray("item_price_overrides").let {
        val res = mutableListOf<SubEventModel.ItemOverride>()
        for (i in 0 until it.length()) {
            val or = it.getJSONObject(i)
            res.add(
                SubEventModel.ItemOverride(
                    item = or.getLong("item"),
                    availableFrom = or.optString("available_from", null),
                    availableUntil = or.optString("available_to", null),
                    price = if (or.isNull("price")) null else BigDecimal(or.optString("price")),
                    disabled = or.optBoolean("disabled", false),
                ),
            )
        }
        res
    }

private fun parseVariationPriceOverrides(json: JSONObject) =
    json.getJSONArray("variation_price_overrides").let {
        val res = mutableListOf<SubEventModel.ItemOverride>()
        for (i in 0 until it.length()) {
            val or = it.getJSONObject(i)
            res.add(
                SubEventModel.ItemOverride(
                    item = or.getLong("variation"),
                    availableFrom = or.optString("available_from", null),
                    availableUntil = or.optString("available_to", null),
                    price = if (or.isNull("price")) null else BigDecimal(or.optString("price")),
                    disabled = or.optBoolean("disabled", false),
                ),
            )
        }
        res
    }

private fun parseHasSeating(json: JSONObject): Boolean {
    return try {
        !json.isNull("seating_plan")
    } catch (e: JSONException) {
        false
    }
}
