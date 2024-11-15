package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.text.SimpleDateFormat
import java.util.Date

class JavaUtilDateAdapter : ColumnAdapter<Date, String> {

    override fun decode(databaseValue: String): Date {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        return df.parse(databaseValue)
    }

    override fun encode(value: Date): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        return df.format(value)
    }
}
