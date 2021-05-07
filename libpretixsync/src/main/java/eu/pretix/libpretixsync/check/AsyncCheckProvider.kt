package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.crypto.isValidSignature
import eu.pretix.libpretixsync.crypto.readPubkeyFromPem
import eu.pretix.libpretixsync.crypto.sig1.TicketProtos
import eu.pretix.libpretixsync.db.*
import eu.pretix.libpretixsync.utils.codec.binary.Base64.decodeBase64
import eu.pretix.libpretixsync.utils.logic.JsonLogic
import eu.pretix.libpretixsync.utils.logic.truthy
import io.requery.BlockingEntityStore
import io.requery.Persistable
import io.requery.query.Condition
import io.requery.query.Result
import io.requery.query.Scalar
import io.requery.query.WhereAndOr
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.IllegalInstantException
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*

class AsyncCheckProvider(private val eventSlug: String, private val dataStore: BlockingEntityStore<Persistable>, listId: Long) : TicketCheckProvider {
    private var sentry: SentryInterface
    private val listId: Long
    fun getSentry(): SentryInterface {
        return sentry
    }

    val event: Event by lazy {
        dataStore.select(Event::class.java)
                .where(Event.SLUG.eq(eventSlug))
                .get().first()
    }

    override fun setSentry(sentry: SentryInterface) {
        this.sentry = sentry
    }

    private fun initJsonLogic(subeventId: Long, tz: DateTimeZone): JsonLogic {
        val jsonLogic = JsonLogic()
        jsonLogic.addOperation("objectList") { l, _ -> l }
        jsonLogic.addOperation("lookup") { l, d -> l?.getOrNull(1) }
        jsonLogic.addOperation("inList") { l, d ->
            (l?.getOrNull(1) as List<*>).contains(
                    l.getOrNull(0)
            )
        }
        jsonLogic.addOperation("isAfter") { l, d ->
            if (l?.size == 2 || (l?.size == 3 && l.getOrNull(2) == null)) {
                (l.getOrNull(0) as DateTime).isAfter(l.getOrNull(1) as DateTime)
            } else if (l?.size == 3) {
                (l.getOrNull(0) as DateTime).plusMinutes(l.getOrNull(2) as Int).isAfter(l.getOrNull(1) as DateTime)
            } else {
                false
            }
        }
        jsonLogic.addOperation("isBefore") { l, d ->
            if (l?.size == 2 || (l?.size == 3 && l.getOrNull(2) == null)) {
                (l.getOrNull(0) as DateTime).isBefore(l.getOrNull(1) as DateTime)
            } else if (l?.size == 3) {
                (l.getOrNull(0) as DateTime).minusMinutes(l.getOrNull(2) as Int).isBefore(l.getOrNull(1) as DateTime)
            } else {
                false
            }
        }
        jsonLogic.addOperation("buildTime") { l, d ->
            val t = l?.getOrNull(0)
            var evjson = event.json
            if (subeventId != 0L) {
                val subevent = dataStore.select(SubEvent::class.java)
                        .where(SubEvent.EVENT_SLUG.eq(eventSlug))
                        .and(SubEvent.SERVER_ID.eq(subeventId))
                        .get().first()
                evjson = subevent.json
            }
            if (t == "custom") {
                ISODateTimeFormat.dateTimeParser().parseDateTime(l.getOrNull(1) as String?)
            } else if (t == "customtime") {
                val time = ISODateTimeFormat.timeParser().parseLocalTime(l.getOrNull(1) as String?)
                val today = DateTime(now()).withZone(tz).toLocalDate()
                try {
                    today.toLocalDateTime(time).toDateTime(tz)
                } catch (e: IllegalInstantException) {
                    // DST gap, let's rather do something wrong than crash :(
                    today.toLocalDateTime(time.minusHours(1)).toDateTime(tz)
                }
            } else if (t == "date_from") {
                ISODateTimeFormat.dateTimeParser().parseDateTime(evjson.getString("date_from"))
            } else if (t == "date_to") {
                ISODateTimeFormat.dateTimeParser().parseDateTime(evjson.optString("date_to"))
            } else if (t == "date_admission") {
                ISODateTimeFormat.dateTimeParser().parseDateTime(evjson.optString("date_admission")
                        ?: evjson.getString("date_from"))
            } else {
                null
            }
        }
        return jsonLogic
    }

    data class SignedTicketData(val seed: String, val item: Long, val variation: Long?, val subevent: Long?)

    private fun decodePretixSig1(qrcode: String): SignedTicketData? {
        val rawbytes: ByteArray
        try {
            rawbytes = decodeBase64(qrcode.reversed().toByteArray(Charset.defaultCharset()))
        } catch (e: Exception) {
            return null
        }
        val version = rawbytes[0].toUByte().toInt()
        if (version != 0x01) {
            return null
        }
        val payloadLength = rawbytes[1].toUByte().toInt().shl(2) + rawbytes[2].toUByte().toInt()
        val signatureLength = rawbytes[3].toUByte().toInt().shl(2) + rawbytes[4].toUByte().toInt()
        val payload = rawbytes.copyOfRange(5, 5 + payloadLength)
        val signature = rawbytes.copyOfRange(5 + payloadLength, 5 + payloadLength + signatureLength)

        val validKeys = event.validKeys?.optJSONArray("pretix_sig1") ?: return null
        for (vki in 0 until validKeys.length()) {
            val vk = validKeys.getString(vki)
            if (isValidSignature(payload, signature, readPubkeyFromPem(decodeBase64(vk.toByteArray(Charset.defaultCharset())).toString(Charset.defaultCharset())))) {
                val ticket = TicketProtos.Ticket.parseFrom(payload)
                return SignedTicketData(
                        ticket.seed,
                        ticket.item,
                        ticket.variation,
                        ticket.subevent
                )
            }
        }
        return null
    }

    private fun checkOfflineWithoutData(list: CheckInList, ticketid: String, type: TicketCheckProvider.CheckInType): TicketCheckProvider.CheckResult {
        val dt = now()
        val decoded = decodePretixSig1(ticketid)
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID)

        if (list.getSubevent_id() != null && list.getSubevent_id() > 0 && list.getSubevent_id() != decoded.subevent) {
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID)
        }

        val is_revoked = dataStore.count(RevokedTicketSecret::class.java)
                .where(RevokedTicketSecret.SECRET.eq(ticketid))
                .get().value()
        if (is_revoked > 0) {
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.REVOKED)
        }

        if (!list.all_items) {
            val is_in_list = dataStore.count(CheckInList_Item::class.java)
                    .where(CheckInList_Item.ITEM_ID.eq(decoded.item))
                    .and(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                    .get().value()
            if (is_in_list == 0) {
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.PRODUCT)
            }
        }

        val item = dataStore.select(Item::class.java)
                .where(Item.SERVER_ID.eq(decoded.item))
                .and(Item.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Item not found")

        val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
        res.scanType = type
        res.ticket = item.internalName
        if (decoded.variation != null && decoded.variation > 0L) {
            try {
                val `var` = item.getVariation(decoded.variation)
                if (`var` != null) {
                    res.variation = `var`.stringValue
                }
            } catch (e: JSONException) {
                sentry.captureException(e)
            }
        }
        var require_attention = false
        try {
            require_attention = item.json.optBoolean("checkin_attention", false)
        } catch (e: JSONException) {
            sentry.captureException(e)
        }
        res.isRequireAttention = require_attention

        val queuedCheckIns = dataStore.select(QueuedCheckIn::class.java)
                .where(QueuedCheckIn.SECRET.eq(ticketid))
                .get().toList().filter {
                    it.getCheckinListId() == listId
                }.sortedWith(compareBy({ it.fullDatetime }, { it.id }))

        // TODO: check revoke list

        val rules = list.rules
        if (type == TicketCheckProvider.CheckInType.ENTRY && rules != null && rules.length() > 0) {
            val data = mutableMapOf<String, Any>()
            val tz = DateTimeZone.forID(event.getTimezone())
            val jsonLogic = initJsonLogic(decoded.subevent ?: 0, tz)
            data.put("product", item.getServer_id().toString())
            data.put("variation", if (decoded.variation != null && decoded.variation > 0) {
                decoded.variation.toString()
            } else {
                ""
            })
            data.put("now", dt)
            data.put("entries_number", queuedCheckIns.filter { it.type == "entry" }.size)
            data.put("entries_today", queuedCheckIns.filter {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate() == dt.withZone(tz).toLocalDate() && it.type == "entry"
            }.size)
            data.put("entries_days", queuedCheckIns.map {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate()
            }.toHashSet().size)

            if (!jsonLogic.applyString(rules.toString(), data, safe = true).truthy) {
                res.type = TicketCheckProvider.CheckResult.Type.RULES
                res.isCheckinAllowed = false
                return res
            }
        }

        val questions = item.questions
        val givenAnswers = JSONArray()
        val required_answers: MutableList<TicketCheckProvider.RequiredAnswer> = ArrayList()
        var ask_questions = false
        for (q in questions) {
            if (!q.isAskDuringCheckin) {
                continue
            }
            ask_questions = true
            required_answers.add(TicketCheckProvider.RequiredAnswer(q, ""))
        }
        if (ask_questions && required_answers.size > 0) {
            res.isCheckinAllowed = true
            res.type = TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED
            res.requiredAnswers = required_answers
        } else {
            val entry_allowed = (
                    type == TicketCheckProvider.CheckInType.EXIT ||
                            list.isAllowMultipleEntries ||
                            queuedCheckIns.isEmpty() ||
                            (list.isAllowEntryAfterExit && queuedCheckIns.last().type == "exit")
                    )
            if (!entry_allowed) {
                res.isCheckinAllowed = false
                res.firstScanned = queuedCheckIns.first().fullDatetime
                res.type = TicketCheckProvider.CheckResult.Type.USED
            } else {
                res.isCheckinAllowed = true
                res.type = TicketCheckProvider.CheckResult.Type.VALID
                val qci = QueuedCheckIn()
                qci.generateNonce()
                qci.setSecret(ticketid)
                qci.setDatetime(dt.toDate())
                qci.setDatetime_string(QueuedCheckIn.formatDatetime(dt.toDate()))
                qci.setAnswers(givenAnswers.toString())
                qci.setEvent_slug(eventSlug)
                qci.setType(type.toString().toLowerCase())
                qci.setCheckinListId(listId)
                dataStore.insert(qci)
            }
        }
        return res
        // todo document missing stuff: rules, badge printing, questions
    }

    override fun check(ticketid: String): TicketCheckProvider.CheckResult {
        return check(ticketid, ArrayList(), false, true, TicketCheckProvider.CheckInType.ENTRY)
    }

    override fun check(ticketid: String, answers: List<Answer>?, ignore_unpaid: Boolean, with_badge_data: Boolean, type: TicketCheckProvider.CheckInType): TicketCheckProvider.CheckResult {
        sentry.addBreadcrumb("provider.check", "offline check started")
        val dt = now()
        val list = dataStore.select(CheckInList::class.java)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not found")

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val tickets = dataStore.select(OrderPosition::class.java)
                .leftJoin(Order::class.java).on(Order.ID.eq(OrderPosition.ORDER_ID))
                .where(OrderPosition.SECRET.eq(ticketid))
                .and(Order.EVENT_SLUG.eq(eventSlug))
                .get().toList()
        if (tickets.size == 0) {
            return checkOfflineWithoutData(list, ticketid, type)
        }
        val position = tickets[0]
        if (list.getSubevent_id() != null && list.getSubevent_id() > 0 && list.getSubevent_id() != position.subeventId) {
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID)
        }
        val item = position.getItem()
        val order = position.getOrder()
        if (!list.all_items) {
            val is_in_list = dataStore.count(CheckInList_Item::class.java)
                    .where(CheckInList_Item.ITEM_ID.eq(item.getId()))
                    .and(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                    .get().value()
            if (is_in_list == 0) {
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.PRODUCT)
            }
        }

        val jPosition: JSONObject
        jPosition = try {
            position.json
        } catch (e: JSONException) {
            sentry.captureException(e)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
        res.scanType = type
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
        res.seat = position.seatName
        res.orderCode = position.getOrder().getCode()
        res.position = jPosition
        var require_attention = position.getOrder().isCheckin_attention
        try {
            require_attention = require_attention || item.json.optBoolean("checkin_attention", false)
        } catch (e: JSONException) {
            sentry.captureException(e)
        }
        res.isRequireAttention = require_attention

        val storedCheckIns = dataStore.select(CheckIn::class.java)
                .where(CheckIn.POSITION_ID.eq(position.getId()))
                .get().toList()
        val checkIns = storedCheckIns.filter {
            it.getListId() == listId
        }.sortedWith(compareBy({ it.fullDatetime }, { it.id }))

        if (order.getStatus() != "p" && order.getStatus() != "n") {
            res.type = TicketCheckProvider.CheckResult.Type.CANCELED
            res.isCheckinAllowed = false
            return res
        }
        if (order.getStatus() != "p" && !(ignore_unpaid && list.include_pending)) {
            res.type = TicketCheckProvider.CheckResult.Type.UNPAID
            res.isCheckinAllowed = list.include_pending
            return res;
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val rules = list.rules
        if (type == TicketCheckProvider.CheckInType.ENTRY && rules != null && rules.length() > 0) {
            val data = mutableMapOf<String, Any>()
            val tz = DateTimeZone.forID(event.getTimezone())
            val jsonLogic = initJsonLogic(position.getSubevent_id(), tz)
            data.put("product", position.getItem().getServer_id().toString())
            data.put("variation", position.getVariation_id().toString())
            data.put("now", dt)
            data.put("entries_number", checkIns.filter { it.type == "entry" }.size)
            data.put("entries_today", checkIns.filter {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate() == dt.withZone(tz).toLocalDate() && it.type == "entry"
            }.size)
            data.put("entries_days", checkIns.map {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate()
            }.toHashSet().size)

            if (!jsonLogic.applyString(rules.toString(), data, safe = true).truthy) {
                res.type = TicketCheckProvider.CheckResult.Type.RULES
                res.isCheckinAllowed = false
                return res
            }
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val questions = item.questions
        val answerMap = position.answers
        if (answers != null) {
            for (a in answers) {
                answerMap[(a.question as Question).getServer_id()] = a.value
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
                } catch (e: QuestionLike.ValidationException) {
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

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        if (ask_questions && required_answers.size > 0) {
            res.isCheckinAllowed = true
            res.type = TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED
            res.requiredAnswers = required_answers
        } else {
            val entry_allowed = (
                    type == TicketCheckProvider.CheckInType.EXIT ||
                            list.isAllowMultipleEntries ||
                            checkIns.isEmpty() ||
                            (list.isAllowEntryAfterExit && checkIns.last().type == "exit")
                    )
            if (!entry_allowed) {
                res.isCheckinAllowed = false
                res.firstScanned = checkIns.first().fullDatetime
                res.type = TicketCheckProvider.CheckResult.Type.USED
            } else {
                res.isCheckinAllowed = true
                res.type = TicketCheckProvider.CheckResult.Type.VALID
                val qci = QueuedCheckIn()
                qci.generateNonce()
                qci.setSecret(ticketid)
                qci.setDatetime(dt.toDate())
                qci.setDatetime_string(QueuedCheckIn.formatDatetime(dt.toDate()))
                qci.setAnswers(givenAnswers.toString())
                qci.setEvent_slug(eventSlug)
                qci.setType(type.toString().toLowerCase())
                qci.setCheckinListId(listId)
                dataStore.insert(qci)
                val ci = CheckIn()
                ci.setListId(listId)
                ci.setPosition(position)
                ci.setType(type.toString().toLowerCase())
                ci.setDatetime(dt.toDate())
                ci.setJson_data("{\"local\": true, \"type\": \"${type.toString().toLowerCase()}\", \"datetime\": \"${QueuedCheckIn.formatDatetime(dt.toDate())}\"}")
                dataStore.insert(ci)
            }
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!
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
                    sr.variation = item.getVariation(position.variationId)?.stringValue
                }
            } catch (e: JSONException) {
                sentry.captureException(e)
            }
            sr.attendee_name = position.attendee_name
            sr.seat = position.seatName
            sr.orderCode = order.getCode()
            sr.secret = position.getSecret()
            val queuedCheckIns = dataStore.count(QueuedCheckIn::class.java)
                    .where(QueuedCheckIn.SECRET.eq(position.getSecret()))
                    .and(QueuedCheckIn.CHECKIN_LIST_ID.eq(listId))
                    .get().value().toLong()
            var is_checked_in = queuedCheckIns > 0
            for (ci in position.getCheckins()) {
                if (ci.getListId() == listId) {
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
            sr.position = position.json
            results.add(sr)
        }
        return results
    }

    private fun basePositionQuery(list: CheckInList, onlyCheckedIn: Boolean): WhereAndOr<out Scalar<Int?>?> {
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

        if (!list.isAll_items) {
            val product_ids = dataStore.select(CheckInList_Item.ITEM_ID)
                    .where(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                    .get().toList().map { it.get<Long>(0) }
            q = q.and(OrderPosition.ITEM_ID.`in`(product_ids))
        }

        if (onlyCheckedIn) {
            q = q.and(OrderPosition.ID.`in`(
                    dataStore.select(CheckIn.POSITION_ID)
                            .where(CheckIn.LIST_ID.eq(list.getServer_id()))
                            .and(CheckIn.TYPE.eq("entry"))
            ))
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
                    val position_count = basePositionQuery(list, false)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(`var`.server_id)).get()!!.value()!!
                    val ci_count = basePositionQuery(list, true)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(`var`.server_id)).get()!!.value()!!
                    variations.add(TicketCheckProvider.StatusResultItemVariation(
                            `var`.server_id,
                            `var`.stringValue,
                            position_count,
                            ci_count
                    ))
                }
                val position_count = basePositionQuery(list, false)
                        .and(OrderPosition.ITEM_ID.eq(product.id)).get()!!.value()!!
                val ci_count = basePositionQuery(list, true)
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
        return TicketCheckProvider.StatusResult(list.name, sum_pos, sum_ci, null, items)
    }

    private var overrideNow: DateTime? = null

    fun setNow(d: DateTime) {
        overrideNow = d
    }

    private fun now(): DateTime {
        return overrideNow ?: DateTime()
    }

    init {
        sentry = DummySentryImplementation()
        this.listId = listId
    }
}