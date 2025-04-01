package eu.pretix.libpretixsync.models

import java.math.BigDecimal
import java.time.OffsetDateTime

data class Discount(
    val id: Long,
    val serverId: Long,
    val eventSlug: String,
    val active: Boolean,
    val position: Long,
    val allSalesChannels: Boolean,
    val limitSalesChannels: List<String>,
    val availableFrom: OffsetDateTime? = null,
    val availableUntil: OffsetDateTime? = null,
    val subeventMode: DiscountSubeventMode,
    val subeventDateFrom: OffsetDateTime? = null,
    val subeventDateUntil: OffsetDateTime? = null,
    val conditionAllProducts: Boolean,
    val conditionLimitProducts: List<Long>,
    val conditionApplyToAddons: Boolean,
    val conditionIgnoreVoucherDiscounted: Boolean,
    val conditionMinCount: Int,
    val conditionMinValue: BigDecimal,
    val benefitSameProducts: Boolean,
    val benefitLimitProducts: List<Long>,
    val benefitDiscountMatchingPercent: BigDecimal,
    val benefitOnlyApplyToCheapestNMatches: Int?,
    val benefitApplyToAddons: Boolean,
    val benefitIgnoreVoucherDiscounted: Boolean,
) {
    enum class DiscountSubeventMode {
        MIXED,
        SAME,
        DISTINCT
    }
}