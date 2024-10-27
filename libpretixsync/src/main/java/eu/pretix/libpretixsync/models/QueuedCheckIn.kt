package eu.pretix.libpretixsync.models

import java.time.OffsetDateTime

data class QueuedCheckIn(
    val id: Long,
    val answers: String?,
    val checkInListId: Long?,
    val dateTime: OffsetDateTime,
    val eventSlug: String?,
    val nonce: String?,
    val secret: String?,
    val sourceType: String?,
    val type: String?,
)
