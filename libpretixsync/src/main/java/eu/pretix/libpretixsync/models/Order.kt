package eu.pretix.libpretixsync.models

data class Order(
    val id: Long,
    val eventSlug: String,
    val requiresCheckInAttention: Boolean,
    val status: Status,
    val code: String? = null,
    val checkInText: String? = null,
    val testMode: Boolean = false,
    val email: String? = null,
) {
    enum class Status(val value: String) {
        PENDING("n"),
        PAID("p"),
        EXPIRED("e"),
        CANCELED("c"),
        ;

        companion object {
            fun fromValue(value: String) =
                when (value) {
                    "n" -> PENDING
                    "p" -> PAID
                    "e" -> EXPIRED
                    "c" -> CANCELED
                    else -> throw IllegalArgumentException()
                }
        }
    }
}
