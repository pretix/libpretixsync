package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AndroidOffsetDateTimeAdapter : ColumnAdapter<OffsetDateTime, String> {
    private val df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXXXX")

    override fun decode(databaseValue: String): OffsetDateTime {
        // Use the default formatter (ISO_OFFSET_DATE_TIME) when decoding to be on the safe side
        // in case we encounter valid ISO strings with a slightly different format (e.g. including
        // milliseconds)
        return OffsetDateTime.parse(databaseValue)
    }

    override fun encode(value: OffsetDateTime): String {
        return df.format(value.atZoneSameInstant(ZoneId.of("Z")))
    }
}
