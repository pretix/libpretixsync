package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.db.*
import io.requery.BlockingEntityStore
import io.requery.Persistable
import io.requery.query.Condition
import io.requery.query.Result
import io.requery.query.Scalar
import io.requery.query.WhereAndOr
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class AsyncCheckProvider(private val eventSlug: String, private val dataStore: BlockingEntityStore<Persistable>, listId: Long) : TicketCheckProvider {
    private var sentry: SentryInterface
    private val listId: Long
    fun getSentry(): SentryInterface {
        return sentry
    }

    override fun setSentry(sentry: SentryInterface) {
        this.sentry = sentry
    }

    override fun check(ticketid: String): TicketCheckProvider.CheckResult {
        return check(ticketid, ArrayList(), false, true)
    }

    override fun check(ticketid: String, answers: List<TicketCheckProvider.Answer>?, ignore_unpaid: Boolean, with_badge_data: Boolean): TicketCheckProvider.CheckResult {
        sentry.addBreadcrumb("provider.check", "offline check started")
        val tickets = dataStore.select(OrderPosition::class.java)
                .leftJoin(Order::class.java).on(Order.ID.eq(OrderPosition.ORDER_ID))
                .where(OrderPosition.SECRET.eq(ticketid))
                .and(Order.EVENT_SLUG.eq(eventSlug))
                .get().toList()
        if (tickets.size == 0) {
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID)
        }
        val position = tickets[0]
        val item = position.getItem()
        val order = position.getOrder()
        val list = dataStore.select(CheckInList::class.java)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not found")
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0 && list.getSubevent_id() != position.subeventId) {
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID)
        }
        if (!list.all_items) {
            val is_in_list = dataStore.count(CheckInList_Item::class.java)
                    .where(CheckInList_Item.ITEM_ID.eq(item.getId()))
                    .and(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                    .get().value()
            if (is_in_list == 0) {
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.PRODUCT)
            }
        }
        val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
        val queuedCheckIns = dataStore.select(QueuedCheckIn::class.java)
                .where(QueuedCheckIn.SECRET.eq(ticketid))
                .and(QueuedCheckIn.CHECKIN_LIST_ID.eq(listId))
                .orderBy(QueuedCheckIn.DATETIME_STRING)
                .get().firstOrNull()
        var is_checked_in = false
        if (queuedCheckIns != null) {
            is_checked_in = true
            res.firstScanned = queuedCheckIns.fullDatetime
        } else {
            for (ci in position.getCheckins()) {
                if (ci.getList().getServer_id() == listId) {
                    is_checked_in = true
                    res.firstScanned = ci.fullDatetime
                    break
                }
            }
        }
        val jPosition: JSONObject
        jPosition = try {
            position.json
        } catch (e: JSONException) {
            sentry.captureException(e)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
        }
        if (order.getStatus() != "p" && order.getStatus() != "n") {
            res.type = TicketCheckProvider.CheckResult.Type.CANCELED
            res.isCheckinAllowed = false
        } else if (order.getStatus() != "p" && !(ignore_unpaid && list.include_pending)) {
            res.type = TicketCheckProvider.CheckResult.Type.UNPAID
            res.isCheckinAllowed = list.include_pending
        } else if (is_checked_in) {
            res.type = TicketCheckProvider.CheckResult.Type.USED
            res.isCheckinAllowed = true
        } else {
            res.isCheckinAllowed = true
            val questions = item.questions
            val answerMap = position.answers
            if (answers != null) {
                for (a in answers) {
                    answerMap[a.question!!.getServer_id()] = a.value
                }
            }
            val givenAnswers = JSONArray()
            val required_answers: MutableList<TicketCheckProvider.RequiredAnswer> = ArrayList()
            var ask_questions = false
            for (q in questions) {
                if (!q.isAskDuringCheckin) {
                    continue
                }
                var answer: String? = ""
                if (answerMap.containsKey(q.getServer_id())) {
                    answer = answerMap[q.getServer_id()]
                    try {
                        answer = q.clean_answer(answer, q.options)
                        val jo = JSONObject()
                        jo.put("answer", answer)
                        jo.put("question", q.getServer_id())
                        givenAnswers.put(jo)
                    } catch (e: AbstractQuestion.ValidationException) {
                        answer = ""
                        ask_questions = true
                    } catch (e: JSONException) {
                        answer = ""
                        ask_questions = true
                    }
                } else {
                    ask_questions = true
                }
                required_answers.add(TicketCheckProvider.RequiredAnswer(q, answer))
            }
            if (ask_questions && required_answers.size > 0) {
                res.type = TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED
                res.requiredAnswers = required_answers
            } else {
                res.type = TicketCheckProvider.CheckResult.Type.VALID
                val qci = QueuedCheckIn()
                qci.generateNonce()
                qci.setSecret(ticketid)
                qci.setDatetime(Date())
                qci.setDatetime_string(QueuedCheckIn.formatDatetime(Date()))
                qci.setAnswers(givenAnswers.toString())
                qci.setEvent_slug(eventSlug)
                qci.setCheckinListId(listId)
                dataStore.insert(qci)
                val ci = CheckIn()
                ci.setList(list)
                ci.setPosition(position)
                ci.setDatetime(Date())
                ci.setJson_data("{\"local\": true, \"datetime\": \"" + QueuedCheckIn.formatDatetime(Date()) + "\"}")
                dataStore.insert(ci)
            }
        }
        res.ticket = position.getItem().internalName
        val varid = position.variationId
        if (varid != null) {
            try {
                val `var` = item.getVariation(varid)
                if (`var` != null) {
                    res.variation = `var`.stringValue
                }
            } catch (e: JSONException) {
                sentry.captureException(e)
            }
        }
        res.attendee_name = position.attendee_name
        res.orderCode = position.getOrder().getCode()
        res.position = jPosition
        var require_attention = position.getOrder().isCheckin_attention
        try {
            require_attention = require_attention || item.json.optBoolean("checkin_attention", false)
        } catch (e: JSONException) {
            sentry.captureException(e)
        }
        res.isRequireAttention = require_attention
        return res
    }

    @Throws(CheckException::class)
    override fun search(query: String, page: Int): List<TicketCheckProvider.SearchResult> {
        var query = query
        sentry.addBreadcrumb("provider.search", "offline search started")
        val results: MutableList<TicketCheckProvider.SearchResult> = ArrayList()
        if (query.length < 4) {
            return results
        }
        val list = dataStore.select(CheckInList::class.java)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: throw CheckException("Check-in list not found")
        val positions: List<OrderPosition>
        var search: Condition<*, *>
        query = query.toUpperCase()
        search = OrderPosition.SECRET.upper().like("$query%")
                .or(OrderPosition.ATTENDEE_NAME.upper().like("%$query%"))
                .or(OrderPosition.ATTENDEE_EMAIL.upper().like("%$query%"))
                .or(Order.EMAIL.upper().like("%$query%"))
                .or(Order.CODE.upper().like("$query%"))
        if (!list.all_items) {
            val itemids: MutableList<Long> = ArrayList()
            for (item in list.items) {
                itemids.add(item.getId())
            }
            search = Item.ID.`in`(itemids).and(search)
        }
        search = Order.EVENT_SLUG.eq(eventSlug).and(search)
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
            search = OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()).and(search)
        }
        // The weird typecasting is apparently due to a bug in the Java compiler
// see https://github.com/requery/requery/issues/229#issuecomment-240470748
        positions = (dataStore.select(OrderPosition::class.java)
                .leftJoin(Order::class.java).on(Order.ID.eq(OrderPosition.ORDER_ID) as Condition<*, *>)
                .leftJoin(Item::class.java).on(Item.ID.eq(OrderPosition.ITEM_ID))
                .where(search).limit(50).offset(50 * (page - 1)).get() as Result<OrderPosition>).toList()
        // TODO: search invoice_address?
        for (position in positions) {
            val item = position.getItem()
            val order = position.getOrder()
            val sr = TicketCheckProvider.SearchResult()
            sr.ticket = item.internalName
            try {
                if (position.variationId != null && position.variationId > 0) {
                    sr.variation = item.getVariation(position.variationId).stringValue
                }
            } catch (e: JSONException) {
                sentry.captureException(e)
            }
            sr.attendee_name = position.attendee_name
            sr.orderCode = order.getCode()
            sr.secret = position.getSecret()
            val queuedCheckIns = dataStore.count(QueuedCheckIn::class.java)
                    .where(QueuedCheckIn.SECRET.eq(position.getSecret()))
                    .and(QueuedCheckIn.CHECKIN_LIST_ID.eq(listId))
                    .get().value().toLong()
            var is_checked_in = queuedCheckIns > 0
            for (ci in position.getCheckins()) {
                if (ci.getList().getServer_id() == listId) {
                    is_checked_in = true
                    break
                }
            }
            sr.isRedeemed = is_checked_in
            if (order.getStatus() == "p") {
                sr.status = TicketCheckProvider.SearchResult.Status.PAID
            } else if (order.getStatus() == "n") {
                sr.status = TicketCheckProvider.SearchResult.Status.PENDING
            } else {
                sr.status = TicketCheckProvider.SearchResult.Status.CANCELED
            }
            var require_attention = position.getOrder().isCheckin_attention
            try {
                require_attention = require_attention || item.json.optBoolean("checkin_attention", false)
            } catch (e: JSONException) {
                sentry.captureException(e)
            }
            sr.isRequireAttention = require_attention
            results.add(sr)
        }
        return results
    }

    private fun basePositionQuery(list: CheckInList): WhereAndOr<out Scalar<Int?>?> {
        val status: MutableList<String> = ArrayList()
        status.add("p")
        if (list.include_pending) {
            status.add("n")
        }
        var q = dataStore.count(OrderPosition::class.java).distinct()
                .leftJoin(Order::class.java).on(OrderPosition.ORDER_ID.eq(Order.ID))
                .where(Order.EVENT_SLUG.eq(eventSlug))
                .and(Order.STATUS.`in`(status))
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
            q = q.and(OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()))
        }
        return q
    }

    private fun baseCheckInQuery(list: CheckInList): WhereAndOr<out Scalar<Int?>?> {
        val status: MutableList<String> = ArrayList()
        status.add("p")
        if (list.include_pending) {
            status.add("n")
        }
        var q = dataStore.count(CheckIn::class.java).distinct()
                .leftJoin(OrderPosition::class.java).on(CheckIn.POSITION_ID.eq(OrderPosition.ID))
                .leftJoin(Order::class.java).on(OrderPosition.ORDER_ID.eq(Order.ID))
                .where(Order.EVENT_SLUG.eq(eventSlug))
                .and(CheckIn.LIST_ID.eq(list.getId()))
                .and(Order.STATUS.`in`(status))
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
            q = q.and(OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()))
        }
        return q
    }

    @Throws(CheckException::class)
    override fun status(): TicketCheckProvider.StatusResult {
        sentry.addBreadcrumb("provider.status", "offline status started")
        val items: MutableList<TicketCheckProvider.StatusResultItem> = ArrayList()
        val list = dataStore.select(CheckInList::class.java)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: throw CheckException("Check-in list not found")
        val products: List<Item>
        products = if (list.all_items) {
            dataStore.select(Item::class.java)
                    .where(Item.EVENT_SLUG.eq(eventSlug))
                    .get().toList()
        } else {
            list.items
        }
        var sum_pos = 0
        var sum_ci = 0
        for (product in products) {
            val variations: MutableList<TicketCheckProvider.StatusResultItemVariation> = ArrayList()
            try {
                for (`var` in product.variations) {
                    val position_count = basePositionQuery(list)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(`var`.server_id)).get()!!.value()!!
                    val ci_count = baseCheckInQuery(list)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(`var`.server_id)).get()!!.value()!!
                    variations.add(TicketCheckProvider.StatusResultItemVariation(
                            `var`.server_id,
                            `var`.stringValue,
                            position_count,
                            ci_count
                    ))
                }
                val position_count = basePositionQuery(list)
                        .and(OrderPosition.ITEM_ID.eq(product.id)).get()!!.value()!!
                val ci_count = baseCheckInQuery(list)
                        .and(OrderPosition.ITEM_ID.eq(product.id)).get()!!.value()!!
                items.add(TicketCheckProvider.StatusResultItem(
                        product.getServer_id(),
                        product.internalName,
                        position_count,
                        ci_count,
                        variations,
                        product.isAdmission
                ))
                sum_pos += position_count
                sum_ci += ci_count
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return TicketCheckProvider.StatusResult(list.name, sum_pos, sum_ci, items)
    }

    init {
        sentry = DummySentryImplementation()
        this.listId = listId
    }
}