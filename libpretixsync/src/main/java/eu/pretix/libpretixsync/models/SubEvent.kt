package eu.pretix.libpretixsync.models

import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime

data class SubEvent(
    val id: Long,
    val name: String,
    val dateFrom: OffsetDateTime,
    val dateTo: OffsetDateTime? = null,
    val itemPriceOverrides: List<ItemOverride>,
    val variationPriceOverrides: List<ItemOverride>,
    val hasSeating: Boolean = false,
    val seatCategoryMapping: JSONObject = JSONObject(),
) {
    data class ItemOverride(
        val item: Long,
        val availableFrom: String? = null,
        val availableUntil: String? = null,
        val price: BigDecimal? = null,
        val disabled: Boolean = false,
    )

    fun getPriceForItem(
        item_id: Long,
        original_price: BigDecimal,
    ): BigDecimal {
        for (or in itemPriceOverrides) {
            if (or.item == item_id) {
                return or.price ?: original_price
            }
        }
        return original_price
    }

    fun getPriceForVariation(
        var_id: Long,
        original_price: BigDecimal,
    ): BigDecimal {
        for (or in variationPriceOverrides) {
            if (or.item == var_id) {
                return or.price ?: original_price
            }
        }
        return original_price
    }

    fun getOverrideForItem(item_id: Long) =
        itemPriceOverrides.firstOrNull { it.item == item_id }

    fun getOverrideForVariation(var_id: Long) =
        variationPriceOverrides.firstOrNull { it.item == var_id }
}
