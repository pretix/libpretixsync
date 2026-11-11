package eu.pretix.libpretixsync.models

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

class Order(
    val id: Long,
    val eventSlug: String,
    val requiresCheckInAttention: Boolean,
    val status: Status,
    val salesChannel: String,
    val code: String? = null,
    val checkInText: String? = null,
    val testMode: Boolean = false,
    val email: String? = null,
    val phone: String? = null,
    val invoiceAddress: JSONObject? = null,
    val invoiceAddressNameParts: JSONObject? = null,
    val requiresApproval: Boolean = false,
    val validIfPending: Boolean = false,
    val total: BigDecimal? = null,
    val pendingTotal: BigDecimal? = null,
    val payments: JSONArray = JSONArray(),
    val refunds: JSONArray = JSONArray(),
    val locale: String? = null,
    val answersByIdentifier: Map<String, String>? = null,
    val answersByIdentifierWithOptionIdentifiers: Map<String, String>? = null,
) {

    val hasValidStatus = when (status) {
        Status.PAID -> true
        Status.PENDING -> validIfPending
        else -> false
    }

    enum class Status(val value: String) {
        PENDING("n"),
        PAID("p"),
        EXPIRED("e"),
        CANCELED("c"),
        ;

        companion object {
            fun fromValue(value: String) =
                when (value) {
                    "n" -> PENDING
                    "p" -> PAID
                    "e" -> EXPIRED
                    "c" -> CANCELED
                    else -> throw IllegalArgumentException()
                }
        }
    }
}
