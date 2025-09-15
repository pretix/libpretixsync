package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.math.BigDecimal
import java.math.RoundingMode

class BigDecimalAdapter : ColumnAdapter<BigDecimal, Double> {
    override fun decode(databaseValue: Double): BigDecimal {
        return BigDecimal.valueOf(databaseValue).setScale(2, RoundingMode.HALF_UP)
    }

    override fun encode(value: BigDecimal): Double {
        if (value.scale() > 2) {
            throw IllegalArgumentException("Should not store value $value in database, too much precision")
        }

        return value.toDouble()
    }
}

/**
 * Converts a Double database value to BigDecimal
 *
 * Applies the same conversion as BigDecimalAdapter.
 * Should be used for values that do not go through adapters (such as SUM() values).
 */
fun Double.toScaledBigDecimal(): BigDecimal = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)

fun Double?.toScaledBigDecimalOrZero(): BigDecimal {
    return if (this != null) {
        BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    }
}
