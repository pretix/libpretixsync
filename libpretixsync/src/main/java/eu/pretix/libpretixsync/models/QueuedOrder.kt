package eu.pretix.libpretixsync.models

data class QueuedOrder(
    val id: Long,
    val error: String?,
    val eventSlug: String?,
    val idempotencyKey: String?,
    val locked: Boolean?,
    val payload: String?,
    val receiptId: Long?,
)
