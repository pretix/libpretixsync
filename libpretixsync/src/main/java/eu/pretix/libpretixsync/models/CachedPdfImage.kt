package eu.pretix.libpretixsync.models

data class CachedPdfImage(
    val id: Long,
    val orderPositionServerId: Long,
    val etag: String,
    val key: String,
)
