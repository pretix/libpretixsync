package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.Closing
import eu.pretix.libpretixsync.models.Closing as ClosingModel
import org.json.JSONArray
import org.json.JSONObject

fun Closing.toModel(): ClosingModel {
    return ClosingModel(
        id = this.id,
        serverId = this.server_id,
        open = this.open_,
        firstReceiptId = this.first_receipt,
        lastReceiptId = this.last_receipt,
        paymentSum = this.payment_sum,
        paymentSumCash = this.payment_sum_cash,
        cashCounted = this.cash_counted,
        invoiceSettings = this.invoice_settings?.let { JSONObject(it) } ?: JSONObject(),
        datetime = this.datetime,
        cashierName = this.cashier_name,
        cashierNumericId = this.cashier_numericid,
        cashierUserId = this.cashier_userid,
        sums = this.json_data?.let { JSONObject(it).optJSONArray("sums") } ?: JSONArray(),
        trainingSums = this.json_data?.let { JSONObject(it).optJSONArray("training_sums") },
        canceled = this.json_data?.let { JSONObject(it).optJSONArray("canceled") } ?: JSONArray(),
        datamodel = this.json_data?.let { JSONObject(it).optLong("datamodel") } ?: 0L,
    )
}
