package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

class JavaUtilDateAdapter : ColumnAdapter<Date, String> {
    private val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    override fun decode(databaseValue: String): Date {
        return Date(LocalDateTime.parse(databaseValue, df).toInstant(ZoneOffset.UTC).toEpochMilli())
    }

    override fun encode(value: Date): String {
        return df.format(Instant.ofEpochMilli(value.time).atZone(ZoneId.of("Z")))
    }
}
