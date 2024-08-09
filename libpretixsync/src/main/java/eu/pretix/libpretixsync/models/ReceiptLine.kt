package eu.pretix.libpretixsync.models

import org.json.JSONArray
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Note: if you change something here, don't forget to modify ReceiptWrapper.updateFromModel too
 */
data class ReceiptLine(
    val id: Long,
    val positionId: Long,
    val type: Type,
    val price: BigDecimal,
    val listedPrice: BigDecimal? = null,
    val priceAfterVoucher: BigDecimal? = null,
    val customPriceInput: BigDecimal? = null,
    val cartId: String? = null,
    val canceled: Boolean = false,
    val canceledBecauseOfReceipt: Boolean = false,
    val saleText: String? = null,
    val isBundled: Boolean = false,
    val addonTo: Long? = null,
    val remoteError: String? = null,
    val voucherCode: String? = null,
    val useReusableMedium: Long? = null,
    val taxRate: BigDecimal? = null,
    val taxRule: Long? = null,
    val taxValue: BigDecimal? = null,
    val eventDateFrom: OffsetDateTime? = null,
    val eventDateTo: OffsetDateTime? = null,
    val subEventServerId: Long? = null,
    val subEventText: String? = null,
    val itemServerId: Long? = null,
    val variationServerId: Long? = null,
    val requestedValidFrom: String? = null,
    val attendeeCity: String? = null,
    val attendeeCompany: String? = null,
    val attendeeCountry: String? = null,
    val attendeeEmail: String? = null,
    val attendeeName: String? = null,
    val attendeeStreet: String? = null,
    val attendeeZipcode: String? = null,
    val seatGuid: String? = null,
    val seatName: String? = null,
    val answers: JSONArray = JSONArray(),
    val giftCardId: Long? = null,
    val giftCardSecret: String? = null,
    val priceCalculatedFromNet: Boolean = false,
) {
    enum class Type(val value: String) {
        PRODUCT_SALE("PRODUCT_SALE"),
        PRODUCT_RETURN("PRODUCT_RETURN"),
        CHANGE_IN("CHANGE_IN"),
        CHANGE_START("CHANGE_START"),
        CHANGE_OUT("CHANGE_OUT"),
        CHANGE_DIFF("CHANGE_DIFF"),
        GIFTCARD_SALE("GIFTCARD_SALE"),
        GIFTCARD_REDEMPTION("GIFTCARD_REDEMPTION"),
        GIFTCARD_PAYOUT("GIFTCARD_PAYOUT"),
        PAY_ORDER("PAY_ORDER"),
        PAY_ORDER_REVERSE("PAY_ORDER_REVERSE"),
        REFUND_ORDER("REFUND_ORDER"),
        NULL("NULL");


        fun isGiftcard(): Boolean {
          return this.toString().startsWith("GIFTCARD_")
        }

        fun isChange(): Boolean {
            return this.toString().startsWith("CHANGE_")
        }
    }

    val hasAttendeeData: Boolean
        get() = !attendeeName.isNullOrEmpty() ||
                !attendeeEmail.isNullOrEmpty() ||
                !attendeeCompany.isNullOrEmpty() ||
                !attendeeStreet.isNullOrEmpty() ||
                !attendeeZipcode.isNullOrEmpty() ||
                !attendeeCity.isNullOrEmpty() ||
                !attendeeCountry.isNullOrEmpty()
}
