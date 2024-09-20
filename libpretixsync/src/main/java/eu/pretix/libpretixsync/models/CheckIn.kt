package eu.pretix.libpretixsync.models

import java.time.OffsetDateTime

data class CheckIn(
    val id: Long,
    val serverId: Long?,
    val datetime: OffsetDateTime?,
    val type: String?,
    val listServerId: Long?,
    val positionId: Long?,
)
