package eu.pretix.libpretixsync.models

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime

data class Closing(
    val id: Long,
    val serverId: Long?,
    val datetime: OffsetDateTime?,
    val open: Boolean,
    val firstReceiptId: Long?,
    val lastReceiptId: Long?,
    val paymentSum: BigDecimal?,
    val paymentSumCash: BigDecimal?,
    val cashCounted: BigDecimal?,
    val invoiceSettings: JSONObject = JSONObject(),
    val cashierName: String?,
    val cashierNumericId: Long?,
    val cashierUserId: String?,
    val sums: JSONArray = JSONArray(),
    val trainingSums: JSONArray?,
    val canceled: JSONArray = JSONArray(),
    val datamodel: Long?
)
