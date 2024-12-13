package eu.pretix.libpretixsync.models

import java.math.BigDecimal

data class TaxRule(
    val id: Long,
    val serverId: Long,
    val rate: BigDecimal = BigDecimal("0.00"),
    val includesTax: Boolean = false,
    val code: String? = null,
)
