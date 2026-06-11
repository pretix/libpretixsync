package eu.pretix.libpretixsync.models

import java.time.OffsetDateTime

data class ReusableMedium(
    val id: Long,
    val serverId: Long?,
    val active: Boolean,
    val customerId: Long?,
    val expires: OffsetDateTime?,
    val identifier: String?,
    val linkedGiftCardId: Long?,
    val type: String?,
)
