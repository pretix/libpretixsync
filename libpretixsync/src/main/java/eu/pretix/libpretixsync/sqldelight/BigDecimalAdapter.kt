package eu.pretix.libpretixsync.sqldelight

import app.cash.sqldelight.ColumnAdapter
import java.math.BigDecimal
import java.math.RoundingMode

class BigDecimalAdapter : ColumnAdapter<BigDecimal, Double> {
    override fun decode(databaseValue: Double): BigDecimal {
        return BigDecimal(databaseValue).setScale(2, RoundingMode.HALF_UP)
    }

    override fun encode(value: BigDecimal): Double {
        return value.toDouble()
    }
}
