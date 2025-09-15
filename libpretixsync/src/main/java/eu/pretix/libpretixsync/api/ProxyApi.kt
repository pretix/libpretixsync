package eu.pretix.libpretixsync.api

import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.QuestionOption
import eu.pretix.libpretixsync.models.db.toModel
import eu.pretix.libpretixsync.sqldelight.SyncDatabase

/**
 * API types for the pretixSCAN Proxy
 *
 * Used as common types between the ProxyCheckProvider and the Proxy API endpoints.
 *
 * They replicate the field names of the old requery models, which were sent in the requests before the SQLDelight
 * migration. This should provide reasonable backwards compatibility.
 */

data class MultiCheckInput(
    val events_and_checkin_lists: Map<String, Long>,
    val ticketid: String,
    val answers: List<CheckInputAnswer>?,
    val ignore_unpaid: Boolean,
    val with_badge_data: Boolean,
    val type: String?,
    val source_type: String?,

    // TODO: Check unused values
    val allowQuestions: Boolean,
    val nonce: String?
)

data class CheckInput(
    val ticketid: String,
    val answers: List<CheckInputAnswer>?,
    val ignore_unpaid: Boolean,
    val with_badge_data: Boolean,
    val type: String?,
    val source_type: String?
)

data class CheckInputAnswer(
    var question: CheckInputQuestion,
    var value: String,
    var options: List<QuestionOption>? = null
) {
    fun toAnswer(db: SyncDatabase): Answer {
        val q = db.questionQueries.selectByServerId(question.server_id).executeAsOne().toModel()
        return Answer(q, value, options)
    }
}

data class CheckInputQuestion(
    val server_id: Long,
)

data class SearchInput(
    val query: String,
    val page: Int,

    // TODO: Check unused values
    val events_and_checkin_lists: Map<String, Long>,
)
