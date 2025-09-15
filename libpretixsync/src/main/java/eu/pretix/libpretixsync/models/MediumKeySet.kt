package eu.pretix.libpretixsync.models

data class MediumKeySet(
    val id: Long,
    val publicId: Long,
    val active: Boolean,
    val mediaType: String?,
    val uidKey: String,
    val diversificationKey: String,
    val organizer: String?,
)
