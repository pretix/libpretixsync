package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.crypto.isValidSignature
import eu.pretix.libpretixsync.crypto.readPubkeyFromPem
import eu.pretix.libpretixsync.crypto.sig1.TicketProtos
import eu.pretix.libpretixsync.db.*
import eu.pretix.libpretixsync.db.Order
import eu.pretix.libpretixsync.utils.cleanInput
import eu.pretix.libpretixsync.utils.codec.binary.Base64
import eu.pretix.libpretixsync.utils.codec.binary.Base64.decodeBase64
import eu.pretix.libpretixsync.utils.logic.JsonLogic
import eu.pretix.libpretixsync.utils.logic.truthy
import io.requery.BlockingEntityStore
import io.requery.Persistable
import io.requery.kotlin.Logical
import io.requery.query.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.joda.time.IllegalInstantException
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*

class AsyncCheckProvider(private val config: ConfigStore, private val dataStore: BlockingEntityStore<Persistable>) : TicketCheckProvider {
    private var sentry: SentryInterface = DummySentryImplementation()

    /*
     */

    override fun setSentry(sentry: SentryInterface) {
        this.sentry = sentry
    }

    private fun storeFailedCheckin(eventSlug: String, listId: Long, error_reason: String, raw_barcode: String, type: TicketCheckProvider.CheckInType, position: Long? = null, item: Long? = null, variation: Long? = null, subevent: Long? = null, nonce: String?) {
        /*
           :<json boolean error_reason: One of ``canceled``, ``invalid``, ``unpaid``, ``product``, ``rules``, ``revoked``,
                                        ``incomplete``, ``already_redeemed``, ``blocked``, ``invalid_time``, or ``error``. Required.
           :<json raw_barcode: The raw barcode you scanned. Required.
           :<json datetime: Internal ID of an order position you matched. Optional.
           :<json type: Type of scan, defaults to ``"entry"``.
           :<json position: Internal ID of an order position you matched. Optional.
           :<json raw_item: Internal ID of an item you matched. Optional.
           :<json raw_variation: Internal ID of an item variationyou matched. Optional.
           :<json raw_subevent: Internal ID of an event series date you matched. Optional.
         */

        val dt = now()
        val jdoc = JSONObject()
        jdoc.put("datetime", QueuedCheckIn.formatDatetime(dt.toDate()))
        if (raw_barcode.contains(Regex("[\\p{C}]"))) {
            jdoc.put("raw_barcode", "binary:" + Base64.encodeBase64(raw_barcode.toByteArray(Charset.defaultCharset())).toString(Charset.defaultCharset()))
        } else {
            jdoc.put("raw_barcode", raw_barcode)
        }
        jdoc.put("type", when (type) {
            TicketCheckProvider.CheckInType.ENTRY -> "entry"
            TicketCheckProvider.CheckInType.EXIT -> "exit"
        })
        jdoc.put("error_reason", error_reason)
        if (nonce != null) jdoc.put("nonce", nonce)
        if (position != null && position > 0) jdoc.put("position", position)
        if (item != null && item > 0) jdoc.put("item", item)
        if (variation != null && variation > 0) jdoc.put("variation", variation)
        if (subevent != null && subevent > 0) jdoc.put("subevent", subevent)

        val qo = QueuedCall()
        val api = PretixApi.fromConfig(config)  // todo: uses wrong http client
        qo.setUrl(api.eventResourceUrl(eventSlug, "checkinlists") + listId + "/failed_checkins/")
        qo.setBody(jdoc.toString())
        qo.setIdempotency_key(NonceGenerator.nextNonce())
        dataStore.insert(qo)
    }

    private fun initJsonLogic(event: Event, subeventId: Long, tz: DateTimeZone): JsonLogic {
        val jsonLogic = JsonLogic()
        jsonLogic.addOperation("objectList") { l, _ -> l }
        jsonLogic.addOperation("lookup") { l, d -> l?.getOrNull(1) }
        jsonLogic.addOperation("inList") { l, d ->
            (l?.getOrNull(1) as List<*>).contains(
                    l.getOrNull(0)
            )
        }
        jsonLogic.addOperation("entries_since") { l, d ->
            ((d as Map<*, *>)["entries_since"] as ((DateTime) -> Int)).invoke(
                l?.getOrNull(
                    0
                ) as DateTime
            )
        }
        jsonLogic.addOperation("entries_before") { l, d ->
            ((d as Map<*, *>)["entries_before"] as ((DateTime) -> Int)).invoke(
                l?.getOrNull(
                    0
                ) as DateTime
            )
        }
        jsonLogic.addOperation("entries_days_since") { l, d ->
            ((d as Map<*, *>)["entries_days_since"] as ((DateTime) -> Int)).invoke(
                l?.getOrNull(
                    0
                ) as DateTime
            )
        }
        jsonLogic.addOperation("entries_days_before") { l, d ->
            ((d as Map<*, *>)["entries_days_before"] as ((DateTime) -> Int)).invoke(
                l?.getOrNull(
                    0
                ) as DateTime
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
                        .where(SubEvent.EVENT_SLUG.eq(event.slug))
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

    private data class SignedTicketData(val seed: String, val item: Long, val variation: Long?, val subevent: Long?, val validFrom: DateTime?, val validUntil: DateTime?)

    @ExperimentalUnsignedTypes
    private fun decodePretixSig1(event: Event, qrcode: String): SignedTicketData? {
        val rawbytes: ByteArray
        try {
            rawbytes = decodeBase64(qrcode.reversed().toByteArray(Charset.defaultCharset()))
        } catch (e: Exception) {
            return null
        }
        if (rawbytes.size == 0) {
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
                        ticket.subevent,
                        if (ticket.hasValidFromUnixTime() && ticket.validFromUnixTime > 0) DateTime(ticket.validFromUnixTime * 1000) else null,
                        if (ticket.hasValidUntilUnixTime() && ticket.validUntilUnixTime > 0) DateTime(ticket.validUntilUnixTime * 1000) else null
                )
            }
        }
        return null
    }

    data class RSAResult(
        val givenAnswers: JSONArray,
        val requiredAnswers:  MutableList<TicketCheckProvider.QuestionAnswer>,
        val shownAnswers: MutableList<TicketCheckProvider.QuestionAnswer>,
        val askQuestions: Boolean,
    )
    private fun extractRequiredShownAnswers(questions: List<Question>, answerMap: Map<Long, String>): RSAResult {
        val givenAnswers = JSONArray()
        val requiredAnswers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
        val shownAnswers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
        var askQuestions = false

        for (q in questions) {
            if (!q.isAskDuringCheckin && !q.isShowDuringCheckin) {
                continue
            }
            var answer: String? = ""
            if (answerMap.containsKey(q.getServer_id())) {
                answer = answerMap[q.getServer_id()]
                try {
                    answer = q.clean_answer(answer, q.options, false)
                    val jo = JSONObject()
                    jo.put("answer", answer)
                    jo.put("question", q.getServer_id())
                    if (q.isAskDuringCheckin) {
                        givenAnswers.put(jo)
                    }
                    if (q.isShowDuringCheckin) {
                        shownAnswers.add(TicketCheckProvider.QuestionAnswer(q, answer))
                    }
                } catch (e: QuestionLike.ValidationException) {
                    answer = ""
                    if (q.isAskDuringCheckin) {
                        askQuestions = true
                    }
                } catch (e: JSONException) {
                    answer = ""
                    if (q.isAskDuringCheckin) {
                        askQuestions = true
                    }
                }
            } else {
                if (q.isAskDuringCheckin) {
                    askQuestions = true
                }
            }
            if (q.isAskDuringCheckin) {
                requiredAnswers.add(TicketCheckProvider.QuestionAnswer(q, answer))
            }
        }

        return RSAResult(givenAnswers, requiredAnswers, shownAnswers, askQuestions)
    }

    private fun checkOfflineWithoutData(eventsAndCheckinLists: Map<String, Long>, ticketid: String, type: TicketCheckProvider.CheckInType, answers: List<Answer>?, nonce: String?, allowQuestions: Boolean): TicketCheckProvider.CheckResult {
        val dt = now()
        val events = dataStore.select(Event::class.java)
                .where(Event.SLUG.`in`(eventsAndCheckinLists.keys.toList()))
                .get().toList()
        var decoded: SignedTicketData? = null
        var event: Event? = null
        for (e in events) {
            decoded = decodePretixSig1(e, ticketid)
            event = e
            if (decoded != null) break
        }
        if (decoded == null || event == null) {
            val firstentry = eventsAndCheckinLists.entries.first()
            storeFailedCheckin(firstentry.key, firstentry.value, "invalid", ticketid, type, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID, offline = true)
        }
        val listId = eventsAndCheckinLists[event.slug] ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not set for event", offline = true)
        val eventSlug = event.slug
        val list = dataStore.select(CheckInList::class.java)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not found", offline = true)

        val is_revoked = dataStore.count(RevokedTicketSecret::class.java)
            .where(RevokedTicketSecret.SECRET.eq(ticketid))
            .get().value()
        if (is_revoked > 0) {
            storeFailedCheckin(eventSlug, listId, "revoked", ticketid, type, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.REVOKED, offline = true)
        }

        val is_blocked = dataStore.count(BlockedTicketSecret::class.java)
                .where(BlockedTicketSecret.SECRET.eq(ticketid))
                .and(BlockedTicketSecret.BLOCKED.eq(true))
                .get().value()
        if (is_blocked > 0) {
            storeFailedCheckin(eventSlug, listId, "blocked", ticketid, type, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.BLOCKED, offline = true)
        }

        if (type != TicketCheckProvider.CheckInType.EXIT) {
            if (decoded.validFrom?.isAfter(now()) == true) {
                storeFailedCheckin(eventSlug, listId, "invalid_time", ticketid, type, item = decoded.item, variation = decoded.variation, subevent = decoded.subevent, nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID_TIME, offline = true)
            }
            if (decoded.validUntil?.isBefore(now()) == true) {
                storeFailedCheckin(eventSlug, listId, "invalid_time", ticketid, type, item = decoded.item, variation = decoded.variation, subevent = decoded.subevent, nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID_TIME, offline = true)
            }
        }

        if (!list.all_items) {
            val is_in_list = dataStore.count(CheckInList_Item::class.java)
                    .leftJoin(Item::class.java).on(CheckInList_Item.ITEM_ID.eq(Item.ID))
                    .where(Item.SERVER_ID.eq(decoded.item))
                    .and(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                    .get().value()
            if (is_in_list == 0) {
                storeFailedCheckin(eventSlug, listId, "product", ticketid, type, subevent = decoded.subevent, nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.PRODUCT, offline = true)
            }
        }

        if (list.getSubevent_id() != null && list.getSubevent_id() > 0 && list.getSubevent_id() != decoded.subevent) {
            storeFailedCheckin(eventSlug, listId, "invalid", ticketid, type, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID, offline = true)
        }

        val item = dataStore.select(Item::class.java)
                .where(Item.SERVER_ID.eq(decoded.item))
                .and(Item.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
        if (item == null) {
            storeFailedCheckin(eventSlug, listId, "product", ticketid, type, subevent = decoded.subevent, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Item not found", offline = true)
        }

        val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, offline = true)
        res.eventSlug = eventSlug
        res.scanType = type
        res.ticket = item.internalName
        val variation = if (decoded.variation != null && decoded.variation!! > 0L) {
            try {
                item.getVariation(decoded.variation) ?: null
            } catch (e: JSONException) {
                sentry.captureException(e)
                null
            }
        } else { null }
        if (variation != null) {
            res.variation = variation.stringValue
        }
        var require_attention = false
        try {
            require_attention = item.json.optBoolean("checkin_attention", false)
        } catch (e: JSONException) {
            sentry.captureException(e)
        }
        res.isRequireAttention = require_attention || (variation?.isCheckin_attention == true)
        res.checkinTexts = listOfNotNull(variation?.checkin_text?.trim(), item.checkin_text?.trim()).filterNot { it.isBlank() }.filterNot { it.isBlank() || it == "null" }

        val queuedCheckIns = dataStore.select(QueuedCheckIn::class.java)
                .where(QueuedCheckIn.SECRET.eq(ticketid))
                .get().toList().filter {
                    it.getCheckinListId() == listId
                }.sortedWith(compareBy({ it.fullDatetime }, { it.id }))

        val rules = list.rules
        if (type == TicketCheckProvider.CheckInType.ENTRY && rules != null && rules.length() > 0) {
            val data = mutableMapOf<String, Any>()
            val tz = DateTimeZone.forID(event.getTimezone())
            val jsonLogic = initJsonLogic(event, decoded.subevent ?: 0, tz)
            data.put("product", item.getServer_id().toString())
            data.put("variation", if (decoded.variation != null && decoded.variation!! > 0) {
                decoded.variation.toString()
            } else {
                ""
            })
            data.put("gate", config.deviceKnownGateID.toString())
            data.put("now", dt)
            data.put("now_isoweekday", dt.withZone(tz).dayOfWeek().get())
            data.put("entries_number", queuedCheckIns.filter { it.type == "entry" }.size)
            data.put("entries_today", queuedCheckIns.filter {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate() == dt.withZone(tz).toLocalDate() && it.type == "entry"
            }.size)
            data.put("entries_since", { cutoff: DateTime ->
                queuedCheckIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isAfter(cutoff.minus(Duration.millis(1))) && it.type == "entry"
                }.size
            })
            data.put("entries_before", { cutoff: DateTime ->
                queuedCheckIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isBefore(cutoff) && it.type == "entry"
                }.size
            })
            data.put("entries_days_since", { cutoff: DateTime ->
                queuedCheckIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isAfter(cutoff.minus(Duration.millis(1))) && it.type == "entry"
                }.map {
                    DateTime(it.fullDatetime).withZone(tz).toLocalDate()
                }.toHashSet().size
            })
            data.put("entries_days_before", { cutoff: DateTime ->
                queuedCheckIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isBefore(cutoff) && it.type == "entry"
                }.map {
                    DateTime(it.fullDatetime).withZone(tz).toLocalDate()
                }.toHashSet().size
            })
            data.put("entries_days", queuedCheckIns.filter { it.type == "entry" }.map {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate()
            }.toHashSet().size)
            val minutes_since_entries = queuedCheckIns.filter { it.type == "entry" }.map {
                Duration(DateTime(it.fullDatetime).withZone(tz), dt).toStandardMinutes().minutes
            }
            data.put("minutes_since_last_entry", minutes_since_entries.minOrNull() ?: -1)
            data.put("minutes_since_first_entry", minutes_since_entries.maxOrNull() ?: -1)
            data.put("entry_status", if (queuedCheckIns.lastOrNull()?.getType() == "entry") {
                "present"
            } else {
                "absent"
            })

            try {
                if (!jsonLogic.applyString(rules.toString(), data, safe = false).truthy) {
                    res.type = TicketCheckProvider.CheckResult.Type.RULES
                    res.isCheckinAllowed = false
                    storeFailedCheckin(
                        eventSlug,
                        listId,
                        "rules",
                        ticketid,
                        type,
                        item = decoded.item,
                        variation = decoded.variation,
                        subevent = decoded.subevent,
                        nonce = nonce
                    )
                    return res
                }
            } catch (e: Throwable) {
                res.type = TicketCheckProvider.CheckResult.Type.RULES
                res.isCheckinAllowed = false
                res.reasonExplanation = "Custom rule evaluation failed ($e)"
                storeFailedCheckin(
                    eventSlug,
                    listId,
                    "rules",
                    ticketid,
                    type,
                    item = decoded.item,
                    variation = decoded.variation,
                    subevent = decoded.subevent,
                    nonce = nonce,
                )
                return res
            }
        }

        val questions = item.questions

        val answerMap = mutableMapOf<Long, String>()
        if (answers != null) {
            for (a in answers) {
                answerMap[(a.question as Question).getServer_id()] = a.value
            }
        }
        var givenAnswers = JSONArray()
        var required_answers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
        var shown_answers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
        var ask_questions = false
        if (type != TicketCheckProvider.CheckInType.EXIT && allowQuestions) {
            val rsa = extractRequiredShownAnswers(questions, answerMap)
            givenAnswers = rsa.givenAnswers
            required_answers = rsa.requiredAnswers
            shown_answers = rsa.shownAnswers
            ask_questions = rsa.askQuestions
        }
        res.shownAnswers = shown_answers

        if (ask_questions && required_answers.size > 0) {
            res.isCheckinAllowed = true
            res.type = TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED
            res.requiredAnswers = required_answers
        } else {
            val entry_allowed = (
                    type == TicketCheckProvider.CheckInType.EXIT ||
                            list.isAllowMultipleEntries ||
                            queuedCheckIns.isEmpty() ||
                            queuedCheckIns.all { it.type == "exit" } ||
                            (list.isAllowEntryAfterExit && queuedCheckIns.last().type == "exit")
                    )
            if (!entry_allowed) {
                res.isCheckinAllowed = false
                res.firstScanned = queuedCheckIns.first().fullDatetime
                res.type = TicketCheckProvider.CheckResult.Type.USED
                storeFailedCheckin(eventSlug, listId, "already_redeemed", ticketid, type, item = decoded.item, variation = decoded.variation, subevent = decoded.subevent, nonce = nonce)
            } else {
                res.isCheckinAllowed = true
                res.type = TicketCheckProvider.CheckResult.Type.VALID
                val qci = QueuedCheckIn()
                if (nonce != null) {
                    qci.setNonce(nonce)
                } else {
                    qci.generateNonce()
                }
                qci.setSecret(ticketid)
                qci.setDatetime(dt.toDate())
                qci.setDatetime_string(QueuedCheckIn.formatDatetime(dt.toDate()))
                qci.setAnswers(givenAnswers.toString())
                qci.setEvent_slug(eventSlug)
                qci.setType(type.toString().lowercase(Locale.getDefault()))
                qci.setCheckinListId(listId)
                dataStore.insert(qci)
            }
        }
        return res
        // todo document missing stuff: rules, badge printing, questions
    }

    override fun check(eventsAndCheckinLists: Map<String, Long>, ticketid: String): TicketCheckProvider.CheckResult {
        return check(eventsAndCheckinLists, ticketid, "barcode", ArrayList(), false, true, TicketCheckProvider.CheckInType.ENTRY)
    }

    override fun check(
        eventsAndCheckinLists: Map<String, Long>,
        ticketid: String,
        source_type: String,
        answers: List<Answer>?,
        ignore_unpaid: Boolean,
        with_badge_data: Boolean,
        type: TicketCheckProvider.CheckInType,
        nonce: String?,
        allowQuestions: Boolean,
    ): TicketCheckProvider.CheckResult {
        val ticketid_cleaned = cleanInput(ticketid, source_type)

        sentry.addBreadcrumb("provider.check", "offline check started")

        val tickets = dataStore.select(OrderPosition::class.java)
            .leftJoin(Order::class.java).on(Order.ID.eq(OrderPosition.ORDER_ID))
            .where(OrderPosition.SECRET.eq(ticketid_cleaned))
            .and(Order.EVENT_SLUG.`in`(eventsAndCheckinLists.keys.toList()))
            .get().toList()
        if (tickets.size == 0) {
            val medium = dataStore.select(ReusableMedium::class.java)
                .leftJoin(OrderPosition::class.java).on(OrderPosition.SERVER_ID.eq(ReusableMedium.LINKED_ORDERPOSITION_ID))
                .leftJoin(Order::class.java).on(Order.ID.eq(OrderPosition.ORDER_ID))
                .where(ReusableMedium.IDENTIFIER.eq(ticketid_cleaned))
                .and(ReusableMedium.TYPE.eq(source_type))
                .and(Order.EVENT_SLUG.`in`(eventsAndCheckinLists.keys.toList()))
                .get().firstOrNull()
            if (medium != null) {
                val tickets = dataStore.select(OrderPosition::class.java)
                    .leftJoin(Order::class.java).on(Order.ID.eq(OrderPosition.ORDER_ID))
                    .where(OrderPosition.SERVER_ID.eq(medium.getLinked_orderposition_id()))
                    .and(Order.EVENT_SLUG.`in`(eventsAndCheckinLists.keys.toList()))
                    .get().toList()
                return checkOfflineWithData(
                    eventsAndCheckinLists,
                    ticketid_cleaned,
                    tickets,
                    answers,
                    ignore_unpaid,
                    type,
                    nonce,
                    allowQuestions,
                )
            }

            return checkOfflineWithoutData(
                eventsAndCheckinLists,
                ticketid_cleaned,
                type,
                answers ?: emptyList(),
                nonce,
                allowQuestions,
            )
        } else if (tickets.size > 1) {
            val eventSlug = tickets[0].getOrder().getEvent_slug()
            storeFailedCheckin(
                eventSlug,
                eventsAndCheckinLists[eventSlug] ?: return TicketCheckProvider.CheckResult(
                    TicketCheckProvider.CheckResult.Type.ERROR,
                    "No check-in list selected",
                    offline = true
                ),
                "ambiguous",
                ticketid_cleaned,
                type,
                position = tickets[0].getServer_id(),
                item = tickets[0].getItem().getServer_id(),
                variation = tickets[0].getVariation_id(),
                subevent = tickets[0].getSubevent_id(),
                nonce = nonce,
            )
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.AMBIGUOUS)
        }
        return checkOfflineWithData(eventsAndCheckinLists, ticketid_cleaned, tickets, answers, ignore_unpaid, type, nonce = nonce, allowQuestions = allowQuestions)
    }

    private fun checkOfflineWithData(eventsAndCheckinLists: Map<String, Long>, secret: String, tickets: List<OrderPosition>, answers: List<Answer>?, ignore_unpaid: Boolean, type: TicketCheckProvider.CheckInType, nonce: String?, allowQuestions: Boolean): TicketCheckProvider.CheckResult {
        // !!! When extending this, also extend checkOfflineWithoutData !!!
        val dt = now()
        val eventSlug = tickets[0].getOrder().getEvent_slug()
        val event = dataStore.select(Event::class.java)
                .where(Event.SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Event not found", offline = true)
        val listId = eventsAndCheckinLists[eventSlug] ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "No check-in list selected", offline = true)
        val list = dataStore.select(CheckInList::class.java)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(eventSlug))
                .get().firstOrNull()
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not found", offline = true)

        val position = if (list.isAddonMatch) {
            // Add-on matching, as per spec, but only if we have data, it's impossible in data-less mode
            val candidates = mutableListOf(tickets[0])
            candidates.addAll(tickets[0].getOrder().getPositions().filter {
                it.addonToId == tickets[0].getServer_id()
            })
            val filteredCandidates = if (!list.all_items) {
                val items = dataStore.select(CheckInList_Item.ITEM_ID)
                        .where(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                        .get().toList().map { it.get<Long>(0) }.toHashSet()
                candidates.filter { candidate -> items.contains(candidate.getItem().getId()) }
            } else {
                // This is a useless configuration that the backend won't allow, but we'll still handle
                // it here for completeness
                candidates
            }
            if (filteredCandidates.isEmpty()) {
                storeFailedCheckin(eventSlug, list.getServer_id(), "product", secret, type, position = tickets[0].getServer_id(), item = tickets[0].getItem().getServer_id(), variation = tickets[0].getVariation_id(), subevent = tickets[0].getSubevent_id(), nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.PRODUCT, offline = true)
            } else if (filteredCandidates.size > 1) {
                storeFailedCheckin(eventSlug, list.getServer_id(), "ambiguous", secret, type, position = tickets[0].getServer_id(), item = tickets[0].getItem().getServer_id(), variation = tickets[0].getVariation_id(), subevent = tickets[0].getSubevent_id(), nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, offline = true)
            }
            filteredCandidates[0]
        } else {
            tickets[0]
        }

        val item = position.getItem()
        val order = position.getOrder()

        val jPosition: JSONObject
        jPosition = try {
            position.json
        } catch (e: JSONException) {
            sentry.captureException(e)
            storeFailedCheckin(eventSlug, list.getServer_id(), "error", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, offline = true)
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, offline = true)
        res.scanType = type
        res.ticket = position.getItem().internalName
        val varid = position.variationId
        val variation = if (varid != null) {
            try {
                item.getVariation(varid)
            } catch (e: JSONException) {
                sentry.captureException(e)
                null
            }
        } else { null }
        if (variation != null) {
            res.variation = variation.stringValue
        }

        res.attendee_name = position.attendee_name
        res.seat = position.seatName
        res.orderCode = position.getOrder().getCode()
        res.positionId = position.getPositionid()
        res.position = jPosition
        res.eventSlug = list.event_slug
        var require_attention = position.getOrder().isCheckin_attention
        try {
            require_attention = require_attention || item.json.optBoolean("checkin_attention", false)
        } catch (e: JSONException) {
            sentry.captureException(e)
        }
        res.isRequireAttention = require_attention || variation?.isCheckin_attention == true
        res.checkinTexts = listOfNotNull(order.checkin_text?.trim(), variation?.checkin_text?.trim(), item.checkin_text?.trim()).filterNot { it.isBlank() || it == "null" }

        val storedCheckIns = dataStore.select(CheckIn::class.java)
                .where(CheckIn.POSITION_ID.eq(position.getId()))
                .get().toList()
        val checkIns = storedCheckIns.filter {
            it.getListId() == listId
        }.sortedWith(compareBy({ it.fullDatetime }, { it.id }))

        if (order.getStatus() != "p" && order.getStatus() != "n") {
            res.type = TicketCheckProvider.CheckResult.Type.CANCELED
            res.isCheckinAllowed = false
            storeFailedCheckin(eventSlug, list.getServer_id(), "canceled", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
            return res
        }

        if (position.isBlocked) {
            res.type = TicketCheckProvider.CheckResult.Type.BLOCKED
            res.isCheckinAllowed = false
            storeFailedCheckin(eventSlug, list.getServer_id(), "blocked", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
            return res
        }

        if (order.status != "p" && order.isRequireApproval) {
            res.type = TicketCheckProvider.CheckResult.Type.UNAPPROVED
            res.isCheckinAllowed = false
            storeFailedCheckin(eventSlug, list.getServer_id(), "unapproved", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
            return res
        }

        if (type != TicketCheckProvider.CheckInType.EXIT) {
            val validFrom = position.validFrom
            if (validFrom != null && validFrom.isAfter(now())) {
                res.type = TicketCheckProvider.CheckResult.Type.INVALID_TIME
                res.isCheckinAllowed = false
                storeFailedCheckin(eventSlug, list.getServer_id(), "invalid_time", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
                return res
            }
            val validUntil = position.validUntil
            if (validUntil != null && validUntil.isBefore(now())) {
                res.type = TicketCheckProvider.CheckResult.Type.INVALID_TIME
                res.isCheckinAllowed = false
                storeFailedCheckin(eventSlug, list.getServer_id(), "invalid_time", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
                return res
            }
        }

        if (!list.all_items) {
            val is_in_list = dataStore.count(CheckInList_Item::class.java)
                    .where(CheckInList_Item.ITEM_ID.eq(item.getId()))
                    .and(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                    .get().value()
            if (is_in_list == 0) {
                storeFailedCheckin(eventSlug, list.getServer_id(), "product", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
                res.type = TicketCheckProvider.CheckResult.Type.PRODUCT
                res.isCheckinAllowed = false
                return res
            }
        }

        if (list.getSubevent_id() != null && list.getSubevent_id() > 0 && list.getSubevent_id() != position.subeventId) {
            storeFailedCheckin(eventSlug, list.getServer_id(), "invalid", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID, offline = true)
        }

        if (!order.isValidStatus && !(ignore_unpaid && list.include_pending)) {
            res.type = TicketCheckProvider.CheckResult.Type.UNPAID
            res.isCheckinAllowed = list.include_pending && !order.isValid_if_pending
            storeFailedCheckin(eventSlug, list.getServer_id(), "unpaid", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
            return res
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val rules = list.rules
        if (type == TicketCheckProvider.CheckInType.ENTRY && rules != null && rules.length() > 0) {
            val data = mutableMapOf<String, Any>()
            val tz = DateTimeZone.forID(event.getTimezone())
            val jsonLogic = initJsonLogic(event, position.getSubevent_id(), tz)
            data.put("product", position.getItem().getServer_id().toString())
            data.put("variation", position.getVariation_id().toString())
            data.put("gate", config.deviceKnownGateID.toString())
            data.put("now", dt)
            data.put("now_isoweekday", dt.withZone(tz).dayOfWeek().get())
            data.put("entries_number", checkIns.filter { it.type == "entry" }.size)
            data.put("entries_today", checkIns.filter {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate() == dt.withZone(tz).toLocalDate() && it.type == "entry"
            }.size)
            data.put("entries_since", { cutoff: DateTime ->
                checkIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isAfter(cutoff.minus(Duration.millis(1))) && it.type == "entry"
                }.size
            })
            data.put("entries_days_since", { cutoff: DateTime ->
                checkIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isAfter(cutoff.minus(Duration.millis(1))) && it.type == "entry"
                }.map {
                    DateTime(it.fullDatetime).withZone(tz).toLocalDate()
                }.toHashSet().size
            })
            data.put("entries_before", { cutoff: DateTime ->
                checkIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isBefore(cutoff) && it.type == "entry"
                }.size
            })
            data.put("entries_days_before", { cutoff: DateTime ->
                checkIns.filter {
                    DateTime(it.fullDatetime).withZone(tz).isBefore(cutoff) && it.type == "entry"
                }.map {
                    DateTime(it.fullDatetime).withZone(tz).toLocalDate()
                }.toHashSet().size
            })
            data.put("entries_days", checkIns.filter { it.type == "entry" }.map {
                DateTime(it.fullDatetime).withZone(tz).toLocalDate()
            }.toHashSet().size)
            val minutes_since_entries = checkIns.filter { it.type == "entry" }.map {
                Duration(DateTime(it.fullDatetime).withZone(tz), dt).toStandardMinutes().minutes
            }
            data.put("minutes_since_last_entry", minutes_since_entries.minOrNull() ?: -1)
            data.put("minutes_since_first_entry", minutes_since_entries.maxOrNull() ?: -1)
            data.put("entry_status", if (checkIns.lastOrNull()?.getType() == "entry") {
                "present"
            } else {
                "absent"
            })

            try {
                if (!jsonLogic.applyString(rules.toString(), data, safe = false).truthy) {
                    res.type = TicketCheckProvider.CheckResult.Type.RULES
                    res.isCheckinAllowed = false
                    storeFailedCheckin(
                        eventSlug,
                        list.getServer_id(),
                        "rules",
                        position.secret,
                        type,
                        position = position.getServer_id(),
                        item = position.getItem().getServer_id(),
                        variation = position.getVariation_id(),
                        subevent = position.getSubevent_id(),
                        nonce = nonce
                    )
                    return res
                }
            } catch (e: Throwable) {
                res.type = TicketCheckProvider.CheckResult.Type.RULES
                res.isCheckinAllowed = false
                res.reasonExplanation = "Custom rule evaluation failed ($e)"
                storeFailedCheckin(
                    eventSlug,
                    list.getServer_id(),
                    "rules",
                    position.secret,
                    type,
                    position = position.getServer_id(),
                    item = position.getItem().getServer_id(),
                    variation = position.getVariation_id(),
                    subevent = position.getSubevent_id(),
                    nonce = nonce
                )
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
        var givenAnswers = JSONArray()
        var required_answers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
        var shown_answers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
        var ask_questions = false
        if (type != TicketCheckProvider.CheckInType.EXIT && allowQuestions) {
            val rsa = extractRequiredShownAnswers(questions, answerMap)
            givenAnswers = rsa.givenAnswers
            required_answers = rsa.requiredAnswers
            shown_answers = rsa.shownAnswers
            ask_questions = rsa.askQuestions
        }
        res.shownAnswers = shown_answers

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
                            checkIns.all { it.type == "exit" } ||
                            (list.isAllowEntryAfterExit && checkIns.last().type == "exit")
                    )
            if (!entry_allowed) {
                res.isCheckinAllowed = false
                res.firstScanned = checkIns.first().fullDatetime
                res.type = TicketCheckProvider.CheckResult.Type.USED
                storeFailedCheckin(eventSlug, list.getServer_id(), "already_redeemed", position.secret, type, position = position.getServer_id(), item = position.getItem().getServer_id(), variation = position.getVariation_id(), subevent = position.getSubevent_id(), nonce = nonce)
            } else {
                res.isCheckinAllowed = true
                res.type = TicketCheckProvider.CheckResult.Type.VALID
                val qci = QueuedCheckIn()
                if (nonce != null) {
                    qci.setNonce(nonce)
                } else {
                    qci.generateNonce()
                }
                qci.setSecret(position.secret)
                qci.setDatetime(dt.toDate())
                qci.setDatetime_string(QueuedCheckIn.formatDatetime(dt.toDate()))
                qci.setAnswers(givenAnswers.toString())
                qci.setEvent_slug(eventSlug)
                qci.setType(type.toString().lowercase(Locale.getDefault()))
                qci.setCheckinListId(listId)
                dataStore.insert(qci)
                val ci = CheckIn()
                ci.setListId(listId)
                ci.setPosition(position)
                ci.setType(type.toString().lowercase(Locale.getDefault()))
                ci.setDatetime(dt.toDate())
                ci.setJson_data("{\"local\": true, \"type\": \"${type.toString().lowercase(Locale.getDefault())}\", \"datetime\": \"${QueuedCheckIn.formatDatetime(dt.toDate())}\"}")
                dataStore.insert(ci)
            }
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!
        return res
    }

    @Throws(CheckException::class)
    override fun search(eventsAndCheckinLists: Map<String, Long>, query: String, page: Int): List<TicketCheckProvider.SearchResult> {
        val query = query.uppercase(Locale.getDefault())
        sentry.addBreadcrumb("provider.search", "offline search started")
        val results: MutableList<TicketCheckProvider.SearchResult> = ArrayList()
        if (query.length < 4) {
            return results
        }

        var search: LogicalCondition<*, *>
        search = OrderPosition.SECRET.upper().like("$query%")
                .or(OrderPosition.ATTENDEE_NAME.upper().like("%$query%"))
                .or(OrderPosition.ATTENDEE_EMAIL.upper().like("%$query%"))
                .or(Order.EMAIL.upper().like("%$query%"))
                .or(Order.CODE.upper().like("$query%"))

        var listfilters: Logical<*, *>? = null
        for (entry in eventsAndCheckinLists.entries) {
            val list = dataStore.select(CheckInList::class.java)
                    .where(CheckInList.SERVER_ID.eq(entry.value))
                    .and(CheckInList.EVENT_SLUG.eq(entry.key))
                    .get().firstOrNull()
                    ?: throw CheckException("Check-in list not found")

            var listfilter: Logical<*, *> = Order.EVENT_SLUG.eq(entry.key)
            if (!list.all_items) {
                val itemids: MutableList<Long> = ArrayList()
                for (item in list.items) {
                    itemids.add(item.getId())
                }
                listfilter = Item.ID.`in`(itemids).and(listfilter)
            }
            if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
                listfilter = OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()).and(listfilter)
            }
            if (listfilters == null) {
                listfilters = listfilter
            } else {
                listfilters = listfilter.or(listfilters)
            }
        }
        search = search.and(listfilters)

        val positions: List<OrderPosition>
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
            val variation = try {
                if (position.variationId != null && position.variationId > 0) {
                    item.getVariation(position.variationId)
                } else {
                    null
                }
            } catch (e: JSONException) {
                sentry.captureException(e)
                null
            }
            if (variation != null) {
                sr.variation = variation.stringValue
            }
            sr.attendee_name = position.attendee_name
            sr.seat = position.seatName
            sr.orderCode = order.getCode()
            sr.positionId = position.getPositionid()
            sr.secret = position.getSecret()
            val queuedCheckIns = dataStore.count(QueuedCheckIn::class.java)
                    .where(QueuedCheckIn.SECRET.eq(position.getSecret()))
                    .and(QueuedCheckIn.CHECKIN_LIST_ID.`in`(eventsAndCheckinLists.values.toList()))
                    .get().value().toLong()
            var is_checked_in = queuedCheckIns > 0
            for (ci in position.getCheckins()) {
                if (eventsAndCheckinLists.containsValue(ci.getListId())) {
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
            var require_attention = order.isCheckin_attention
            try {
                require_attention = require_attention || item.json.optBoolean("checkin_attention", false) || variation?.isCheckin_attention == true
            } catch (e: JSONException) {
                sentry.captureException(e)
            }
            sr.isRequireAttention = require_attention
            sr.position = position.json
            results.add(sr)
        }
        return results
    }

    private fun basePositionQuery(lists: List<CheckInList>, onlyCheckedIn: Boolean): WhereAndOr<out Scalar<Int?>?> {

        var q = dataStore.count(OrderPosition::class.java).distinct()
                .leftJoin(Order::class.java).on(OrderPosition.ORDER_ID.eq(Order.ID))
                .where(OrderPosition.SERVER_ID.eq(-1))  // stupid logic node just so we can dynamically add .or() below

        for (list in lists) {
            var lq: Logical<*, *> = Order.EVENT_SLUG.eq(list.getEvent_slug())
            if (list.include_pending) {
                lq = lq.and(Order.STATUS.`in`(listOf("p", "n")))
            } else {
                lq = lq.and(Order.STATUS.eq("p").or(Order.STATUS.eq("n").and(Order.VALID_IF_PENDING.eq(true))))
            }

            if (list.getSubevent_id() != null && list.getSubevent_id() > 0) {
                lq = lq.and(OrderPosition.SUBEVENT_ID.eq(list.getSubevent_id()))
            }

            if (!list.isAll_items) {
                val product_ids = dataStore.select(CheckInList_Item.ITEM_ID)
                        .where(CheckInList_Item.CHECK_IN_LIST_ID.eq(list.getId()))
                        .get().toList().map { it.get<Long>(0) }
                lq = lq.and(OrderPosition.ITEM_ID.`in`(product_ids))
            }

            if (onlyCheckedIn) {
                lq = lq.and(OrderPosition.ID.`in`(
                        dataStore.select(CheckIn.POSITION_ID)
                                .where(CheckIn.LIST_ID.eq(list.getServer_id()))
                                .and(CheckIn.TYPE.eq("entry"))
                ))
            }
            q = q.or(lq)
        }

        return q
    }

    @Throws(CheckException::class)
    override fun status(eventSlug: String, listId: Long): TicketCheckProvider.StatusResult {
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
                    val position_count = basePositionQuery(listOf(list), false)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(`var`.server_id)).get()!!.value()!!
                    val ci_count = basePositionQuery(listOf(list), true)
                            .and(OrderPosition.ITEM_ID.eq(product.id))
                            .and(OrderPosition.VARIATION_ID.eq(`var`.server_id)).get()!!.value()!!
                    variations.add(TicketCheckProvider.StatusResultItemVariation(
                            `var`.server_id,
                            `var`.stringValue,
                            position_count,
                            ci_count
                    ))
                }
                val position_count = basePositionQuery(listOf(list), false)
                        .and(OrderPosition.ITEM_ID.eq(product.id)).get()!!.value()!!
                val ci_count = basePositionQuery(listOf(list), true)
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
}
