package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.QueuedCheckIn
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import eu.pretix.libpretixsync.models.QueuedCheckIn as QueuedCheckInModel

fun QueuedCheckIn.toModel(): QueuedCheckInModel {
    return QueuedCheckInModel(
        id = this.id,
        answers = this.answers,
        checkInListId = this.checkinListId,
        dateTime = parseDateTime(this.datetime!!, this.datetime_string),
        eventSlug = this.event_slug,
        nonce = this.nonce,
        secret = this.secret,
        sourceType = this.source_type,
        type = this.type,
    )
}

private fun parseDateTime(datetime: Date, dateTimeString: String?): OffsetDateTime {
    return if (dateTimeString != null && dateTimeString != "") {
        OffsetDateTime.parse(dateTimeString)
    } else {
        // Assume UTC if we have no additional info
        datetime.toInstant().atOffset(ZoneOffset.UTC)
    }
}
