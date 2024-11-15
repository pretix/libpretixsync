package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class AndroidUtilDateAdapter : ColumnAdapter<Date, String> {
    override fun decode(databaseValue: String): Date {
        try {
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return df.parse(databaseValue)
        } catch (e: Throwable) {
            throw e
        }
    }

    override fun encode(value: Date): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return df.format(value)
    }
}
