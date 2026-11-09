package eu.pretix.libpretixsync.db


data class QuestionOption(
    @Deprecated("Remove after pretix 2026.10 is no longer supported")
    val serverId: Long? = null,
    val position: Long,
    val identifier: String,
    val value: String
) {

    override fun toString(): String {
        return value
    }
}
