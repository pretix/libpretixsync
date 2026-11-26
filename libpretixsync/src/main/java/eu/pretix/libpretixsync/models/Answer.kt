package eu.pretix.libpretixsync.models

open class Answer(
    val datafield: String,
    val answer: String,
    val optionIds: List<Long>,
    val optionIdentifiers: List<String>,
)
