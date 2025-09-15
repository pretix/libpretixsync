package eu.pretix.libpretixsync.models

data class ReusableMedium(
    val id: Long,
    val serverId: Long?,
    val active: Boolean,
    val customerId: Long?,
    val expires: String?,
    val identifier: String?,
    val linkedGiftCardId: Long?,
    val linkedOrderPositionServerId: Long?,
    val type: String?,
)
