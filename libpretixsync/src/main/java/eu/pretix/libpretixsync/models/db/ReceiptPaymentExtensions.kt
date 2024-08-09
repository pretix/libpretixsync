package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.ReceiptPayment
import org.json.JSONObject
import eu.pretix.libpretixsync.models.ReceiptPayment as ReceiptPaymentModel

fun ReceiptPayment.toModel(): ReceiptPaymentModel {
    return ReceiptPaymentModel(
        id = this.id,
        amount = this.amount,
        detailsJson = this.detailsJson?.let { JSONObject(it) },
        paymentType = payment_type,
        receipt = this.receipt,
        status = this.status,
    )
}
