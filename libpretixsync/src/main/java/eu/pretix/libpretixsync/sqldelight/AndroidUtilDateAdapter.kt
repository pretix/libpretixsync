package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class AndroidUtilDateAdapter : ColumnAdapter<Date, String> {
    private val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun decode(databaseValue: String): Date {
        try {
            return df.parse(databaseValue)
        } catch (e: Throwable) {
            throw e
        }
    }

    override fun encode(value: Date): String {
        return df.format(value)
    }
}
