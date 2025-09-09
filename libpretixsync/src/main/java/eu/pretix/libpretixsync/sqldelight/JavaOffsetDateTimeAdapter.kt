package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class JavaOffsetDateTimeAdapter : ColumnAdapter<OffsetDateTime, String> {
    private val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    override fun decode(databaseValue: String): OffsetDateTime {
        // Values are stored with an offset of +00:00 in the database
        return LocalDateTime.parse(databaseValue, df).atOffset(ZoneOffset.UTC)
    }

    override fun encode(value: OffsetDateTime): String {
        // Since the date format just drops the offset information, we must convert to +00:00 first
        return df.format(value.atZoneSameInstant(ZoneOffset.UTC))
    }
}
