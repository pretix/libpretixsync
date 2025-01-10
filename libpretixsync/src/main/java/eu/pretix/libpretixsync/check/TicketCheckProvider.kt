package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.models.db.toModel
import eu.pretix.libpretixsync.sqldelight.Question
import eu.pretix.libpretixsync.models.Question as QuestionModel
import org.json.JSONObject
import java.util.*

interface TicketCheckProvider {
    enum class CheckInType {
        ENTRY, EXIT
    }

    // Old clients expect the requery models on the API
    // This class mimics the relevant fields
    // TODO: List affected versions?
    data class QuestionOutput(
        val server_id: Long,
        val position: Long,
        val required: Boolean,
        val json_data: String,
    ) {
        constructor(model: QuestionModel, jsonData: String) : this(
            server_id = model.serverId,
            required = model.required,
            position = model.position,
            json_data = jsonData,
        )

        fun toModel() = Question(
            server_id = server_id,
            position = position,
            required = required,
            json_data = json_data,
            id = -1L,
            event_slug = null,
        ).toModel()
    }

    class QuestionAnswer {
        private lateinit var _question: QuestionModel
        private lateinit var _jsonData: String

        var currentValue: String? = null

        val question: QuestionOutput
            get() = QuestionOutput(_question, _jsonData)

        constructor(question: QuestionModel, jsonData: String, currentValue: String?) {
            this._question = question
            this._jsonData = jsonData
            this.currentValue = currentValue
        }

        // required for de-serialization
        constructor() {}
    }

    class CheckResult {
        enum class Type {
            INVALID, VALID, USED, ERROR, UNPAID, BLOCKED, INVALID_TIME, CANCELED, PRODUCT, RULES,
            ANSWERS_REQUIRED, AMBIGUOUS, REVOKED, UNAPPROVED
        }

        var type: Type? = null
        var scanType: CheckInType = CheckInType.ENTRY
        var ticket: String? = null
        var variation: String? = null
        var attendee_name: String? = null
        var seat: String? = null
        var message: String? = null
        var orderCode: String? = null
        var positionId: Long? = null
        var firstScanned: Date? = null
        var addonText: String? = null
        var reasonExplanation: String? = null
        var checkinTexts: List<String>? = null
        var isRequireAttention = false
        var isCheckinAllowed = false
        var requiredAnswers: List<QuestionAnswer>? = null
        var shownAnswers: List<QuestionAnswer>? = null
        var position: JSONObject? = null
        var eventSlug: String? = null
        var offline: Boolean = false

        constructor(type: Type?, message: String?, offline: Boolean = false) {
            this.type = type
            this.message = message
            this.offline = offline
        }

        constructor(type: Type?, offline: Boolean = false) {
            this.type = type
            this.offline = offline
        }

        constructor() {  // required for de-serialization
        }

        fun orderCodeAndPositionId(): String? {
            if (orderCode != null && positionId != null && positionId!! > 0) {
                return "${orderCode}-${positionId}"
            } else {
                return orderCode
            }
        }
    }

    class SearchResult {
        enum class Status {
            PAID, CANCELED, PENDING
        }

        var secret: String? = null
        var ticket: String? = null
        var variation: String? = null
        var attendee_name: String? = null
        var seat: String? = null
        var orderCode: String? = null
        var positionId: Long? = null
        var addonText: String? = null
        var status: Status? = null
        var isRedeemed = false
        var isRequireAttention = false
        var position: JSONObject? = null

        constructor() {}
        constructor(r: SearchResult) {
            secret = r.secret
            ticket = r.ticket
            variation = r.variation
            attendee_name = r.attendee_name
            seat = r.seat
            orderCode = r.orderCode
            positionId = r.positionId
            status = r.status
            isRedeemed = r.isRedeemed
            isRequireAttention = r.isRequireAttention
            addonText = r.addonText
            position = r.position
        }

        fun orderCodeAndPositionId(): String? {
            if (orderCode != null && positionId != null && positionId!! > 0) {
                return "${orderCode}-${positionId}"
            } else {
                return orderCode
            }
        }
    }

    class StatusResultItemVariation(var id: Long, var name: String?, var total: Int, var checkins: Int) {
    }

    class StatusResultItem(var id: Long, var name: String?, var total: Int, var checkins: Int, var variations: List<StatusResultItemVariation>?, admission: Boolean) {
        var isAdmission = admission
    }

    class StatusResult(var eventName: String?, var totalTickets: Int, var alreadyScanned: Int, var currentlyInside: Int?, var items: List<StatusResultItem>?) {
    }

    fun check(eventsAndCheckinLists: Map<String, Long>, ticketid: String, source_type: String, answers: List<Answer>?, ignore_unpaid: Boolean, with_badge_data: Boolean, type: CheckInType, nonce: String? = null, allowQuestions: Boolean = true): CheckResult
    fun check(eventsAndCheckinLists: Map<String, Long>, ticketid: String): CheckResult
    @Throws(CheckException::class)
    fun search(eventsAndCheckinLists: Map<String, Long>, query: String, page: Int): List<SearchResult>

    @Throws(CheckException::class)
    fun status(eventSlug: String, listId: Long): StatusResult?

    fun setSentry(sentry: SentryInterface)
}
