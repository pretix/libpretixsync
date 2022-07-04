package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.Question
import org.json.JSONObject
import java.util.*

interface TicketCheckProvider {
    enum class CheckInType {
        ENTRY, EXIT
    }

    class RequiredAnswer {
        lateinit var question: Question
        var currentValue: String? = null

        constructor(question: Question, current_value: String?) {
            this.question = question
            this.currentValue = current_value
        }

        constructor() {  // required for de-serialization
        }

        fun setCurrent_value(current_value: String?) {
            currentValue = current_value
        }
    }

    class CheckResult {
        enum class Type {
            INVALID, VALID, USED, ERROR, UNPAID, CANCELED, PRODUCT, RULES, ANSWERS_REQUIRED,
            AMBIGUOUS, REVOKED
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
        var isRequireAttention = false
        var isCheckinAllowed = false
        var requiredAnswers: List<RequiredAnswer>? = null
        var position: JSONObject? = null

        constructor(type: Type?, message: String?) {
            this.type = type
            this.message = message
        }

        constructor(type: Type?) {
            this.type = type
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

    fun check(ticketid: String, answers: List<Answer>?, ignore_unpaid: Boolean, with_badge_data: Boolean, type: CheckInType): CheckResult
    fun check(ticketid: String): CheckResult
    @Throws(CheckException::class)
    fun search(query: String, page: Int): List<SearchResult>

    @Throws(CheckException::class)
    fun status(): StatusResult?

    fun setSentry(sentry: SentryInterface)
}