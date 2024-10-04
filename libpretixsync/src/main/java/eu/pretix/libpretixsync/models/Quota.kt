package eu.pretix.libpretixsync.models

data class Quota(
    val id: Long,
    val serverId: Long,
    val available: Boolean,
    val availableNumber: Long? = null,
    val size: Long? = null,
    val eventSlug: String? = null,
    val subEventServerId: Long? = null,
    val items: List<Long>,
    val variations: List<Long>,
    val isUnlimited: Boolean = false,
)
