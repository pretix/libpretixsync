package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.QueuedCheckIn
import java.time.OffsetDateTime
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

private fun parseDateTime(datetime: OffsetDateTime, dateTimeString: String?): OffsetDateTime {
    return if (dateTimeString != null && dateTimeString != "") {
        OffsetDateTime.parse(dateTimeString)
    } else {
        datetime
    }
}
