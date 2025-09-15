package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

class AndroidUtilDateAdapter : ColumnAdapter<Date, String> {
    private val df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXXXX")

    override fun decode(databaseValue: String): Date {
        // Use the default formatter (ISO_OFFSET_DATE_TIME) when decoding to be on the safe side
        // in case we encounter valid ISO strings with a slightly different format (e.g. including
        // milliseconds)
        return Date(OffsetDateTime.parse(databaseValue).toInstant().toEpochMilli())
    }

    override fun encode(value: Date): String {
        return df.format(Instant.ofEpochMilli(value.time).atZone(ZoneId.of("Z")))
    }
}
