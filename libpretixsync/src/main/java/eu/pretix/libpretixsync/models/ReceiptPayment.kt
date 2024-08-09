package eu.pretix.libpretixsync.models

import org.json.JSONObject
import java.math.BigDecimal

data class ReceiptPayment(
    val id: Long,
    val amount: BigDecimal?,
    val detailsJson: JSONObject?,
    val paymentType: String?,
    val receipt: Long?,
    val status: String?,
)
