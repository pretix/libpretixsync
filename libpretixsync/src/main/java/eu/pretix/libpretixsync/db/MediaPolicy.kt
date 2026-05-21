package eu.pretix.libpretixsync.db

enum class MediaPolicy(val serverName: String?) {
    NONE(null),
    REUSE("reuse"),
    NEW("new"),
    REUSE_OR_NEW("reuse_or_new")
}