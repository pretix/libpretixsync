package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.text.SimpleDateFormat
import java.util.Date

class JavaUtilDateAdapter : ColumnAdapter<Date, String> {
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    override fun decode(databaseValue: String): Date {
        return df.parse(databaseValue)
    }

    override fun encode(value: Date): String {
        return df.format(value)
    }
}
