package eu.pretix.libpretixsync.models

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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
) {
    companion object {
        // Migrated from AbstractQueuedCheckIn
        // TODO: Find a better place for this?
        fun formatDatetime(date: OffsetDateTime): String {
            val df = DateTimeFormatter.ofPattern(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", // Quoted "Z" to indicate UTC, no timezone offset
                Locale.ENGLISH
            ).withZone(ZoneOffset.UTC)

            return df.format(date)
        }
    }
}
