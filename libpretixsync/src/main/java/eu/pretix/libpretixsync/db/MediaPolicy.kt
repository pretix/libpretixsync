package eu.pretix.libpretixsync.db

enum class MediaPolicy(val serverName: String?) {
    NONE(null),
    REUSE("reuse"),
    NEW("new"),
    REUSE_OR_NEW("reuse_or_new");

    companion object {
        private val map = entries.associateBy(MediaPolicy::serverName)
        fun getByServerName(serverName: String?) = map[serverName]
    }
}