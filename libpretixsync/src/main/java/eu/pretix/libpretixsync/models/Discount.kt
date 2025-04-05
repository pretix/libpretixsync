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

    companion object {
        /**
         * only for use in test, not in production code, since the defaults might change server-side
         * and we should not rely on them
         */
        fun withDefaults(
            id: Long = 1L,
            serverId: Long = 1L,
            eventSlug: String = "democon",
            active: Boolean = true,
            position: Long = 1L,
            allSalesChannels: Boolean = true,
            limitSalesChannels: List<String> = emptyList(),
            availableFrom: OffsetDateTime? = null,
            availableUntil: OffsetDateTime? = null,
            subeventMode: DiscountSubeventMode = DiscountSubeventMode.MIXED,
            subeventDateFrom: OffsetDateTime? = null,
            subeventDateUntil: OffsetDateTime? = null,
            conditionAllProducts: Boolean = true,
            conditionLimitProducts: List<Long> = emptyList(),
            conditionApplyToAddons: Boolean = true,
            conditionIgnoreVoucherDiscounted: Boolean = false,
            conditionMinCount: Int = 0,
            conditionMinValue: BigDecimal = BigDecimal("0.00"),
            benefitSameProducts: Boolean = true,
            benefitLimitProducts: List<Long> = emptyList(),
            benefitDiscountMatchingPercent: BigDecimal = BigDecimal("0.00"),
            benefitOnlyApplyToCheapestNMatches: Int? = null,
            benefitApplyToAddons: Boolean = true,
            benefitIgnoreVoucherDiscounted: Boolean = false,
        ): Discount {
            return Discount(
                id = id,
                serverId = serverId,
                eventSlug = eventSlug,
                active = active,
                position = position,
                allSalesChannels = allSalesChannels,
                limitSalesChannels = limitSalesChannels,
                availableFrom = availableFrom,
                availableUntil = availableUntil,
                subeventMode = subeventMode,
                subeventDateFrom = subeventDateFrom,
                subeventDateUntil = subeventDateUntil,
                conditionAllProducts = conditionAllProducts,
                conditionLimitProducts = conditionLimitProducts,
                conditionApplyToAddons = conditionApplyToAddons,
                conditionIgnoreVoucherDiscounted = conditionIgnoreVoucherDiscounted,
                conditionMinCount = conditionMinCount,
                conditionMinValue = conditionMinValue,
                benefitSameProducts = benefitSameProducts,
                benefitLimitProducts = benefitLimitProducts,
                benefitDiscountMatchingPercent = benefitDiscountMatchingPercent,
                benefitOnlyApplyToCheapestNMatches = benefitOnlyApplyToCheapestNMatches,
                benefitApplyToAddons = benefitApplyToAddons,
                benefitIgnoreVoucherDiscounted = benefitIgnoreVoucherDiscounted
            )
        }
    }
}