package eu.pretix.libpretixsync.models

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        fun formatDatetime(date: Date): String {
            val tz = TimeZone.getTimeZone("UTC")
            val df: DateFormat = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.ENGLISH
            ) // Quoted "Z" to indicate UTC, no timezone offset
            df.timeZone = tz
            return df.format(date)
        }
    }
}
