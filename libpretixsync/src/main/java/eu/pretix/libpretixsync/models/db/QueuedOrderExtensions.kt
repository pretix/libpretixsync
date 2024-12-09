package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.QueuedOrder
import eu.pretix.libpretixsync.models.QueuedOrder as QueuedOrderModel

fun QueuedOrder.toModel(): QueuedOrderModel {
    return QueuedOrderModel(
        id = this.id,
        error = this.error,
        eventSlug = this.event_slug,
        idempotencyKey = this.idempotency_key,
        locked = this.locked,
        payload = this.payload,
        receiptId = this.receipt,
    )
}
