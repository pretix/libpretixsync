package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter

class PostgresLongBooleanAdapter : ColumnAdapter<Boolean, Long> {
    override fun decode(databaseValue: Long): Boolean =
        when (databaseValue) {
            1L -> true
            0L -> false
            else -> throw IllegalArgumentException("Value must be 0L or 1L")
        }

    override fun encode(value: Boolean): Long =
        if (value) 1L else 0L
}
