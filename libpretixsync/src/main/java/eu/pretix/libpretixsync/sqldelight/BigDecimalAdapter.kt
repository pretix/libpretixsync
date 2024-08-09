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
