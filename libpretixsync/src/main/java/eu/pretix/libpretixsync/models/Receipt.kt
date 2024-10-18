package eu.pretix.libpretixsync.models

import java.util.Date

data class Receipt(
    val id: Long,
    val eventSlug: String,
    val paymentType: PaymentType,
    val currency: String,
    val dateTimeOpened: Date,
    val dateTimeClosed: Date? = null,
    val isTraining: Boolean,
    val isCanceled: Boolean,
    val isOpen: Boolean = false,
    val isStarted: Boolean = false,
    val isPrinted: Boolean = false,
    val orderCode: String? = null,
    val fiscalisationData: String? = null,
    val fiscalisationText: String? = null,
    val fiscalisationQr: String? = null,
    val cashierName: String? = null,
    val cashierNumericId: Long? = null,
    val cashierUserId: String? = null,
    val chosenCartId: String? = null,
    val emailTo: String? = null,
    val closing: Long? = null,
    val additionalText: String? = null,
) {
    enum class PaymentType(val value: String) {
        CASH("cash"),
        SUMUP("sumup"),
        IZETTLE("izettle"),
        IZETTLE_QRC("izettle_qrc"),
        STRIPE_TERMINAL("stripe_terminal"),
        TERMINAL_ZVT("terminal_zvt"),
        SQUARE_POS("square_pos"),
        EXTERNAL("external"),
        TERMINAL_CSB60("terminal_csb60"),
        ADYEN_LEGACY("adyen_legacy"),
    }
}
