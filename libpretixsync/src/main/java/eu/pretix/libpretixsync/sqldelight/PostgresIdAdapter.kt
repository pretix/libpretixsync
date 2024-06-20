package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter

/**
 * Column adapter that converts serial / integer ID column values to Long
 *
 * Needed since requery generated all Postgres tables with a 4 byte ID while the rest of the code
 * expects 8 byte IDs / Kotlin Long values.
 * Since only `id` columns (i.e. local IDs) are affected, the conversion should be reasonably safe.
 * If any of the values turn out to be too large for an integer, this adapter will throw an exception.
 */
class PostgresIdAdapter : ColumnAdapter<Long, Int> {
    override fun decode(databaseValue: Int): Long {
        return databaseValue.toLong()
    }

    override fun encode(value: Long): Int {
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
            throw IllegalStateException("ID value exceeds integer range")
        }

        return value.toInt()
    }
}
