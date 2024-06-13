package eu.pretix.libpretixsync.sqldelight

import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Mapper for OffsetDateTime values that are stored as text in the database.
 *
 * This class has the same job as a ColumnAdapter, but since some of the values require mapping from
 * non-null database values to null (e.g. the string "null"), we cannot use custom column types
 * with ColumnAdapters.
 */
object SafeOffsetDateTimeMapper {
    private val df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXXXX")

    fun decode(databaseValue: String?): OffsetDateTime? =
        when (databaseValue) {
            null -> null
            "null" -> null
            else -> OffsetDateTime.parse(databaseValue)
        }

    fun decode(json: JSONObject, key: String): OffsetDateTime? =
        if (json.isNull(key)) {
            null
        } else {
            decode(json.getString(key))
        }

    fun encode(value: OffsetDateTime?): String? =
        when (value) {
            null -> null
            else -> {
                // Use .format() instead of .toString() to get a consistent length
                // OffsetDateTime.toString() omits portions of the date that are zero
                value.format(df)
            }
    }
}
