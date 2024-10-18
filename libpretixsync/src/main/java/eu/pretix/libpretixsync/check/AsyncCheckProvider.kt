package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.crypto.isValidSignature
import eu.pretix.libpretixsync.crypto.readPubkeyFromPem
import eu.pretix.libpretixsync.crypto.sig1.TicketProtos
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.NonceGenerator
import eu.pretix.libpretixsync.db.Order
import eu.pretix.libpretixsync.db.OrderPosition
import eu.pretix.libpretixsync.db.QuestionLike
import eu.pretix.libpretixsync.db.QueuedCall
import eu.pretix.libpretixsync.db.QueuedCheckIn
import eu.pretix.libpretixsync.models.CheckIn
import eu.pretix.libpretixsync.models.CheckInList
import eu.pretix.libpretixsync.models.Event
import eu.pretix.libpretixsync.models.Order as OrderModel
import eu.pretix.libpretixsync.models.OrderPosition as OrderPositionModel
import eu.pretix.libpretixsync.models.Question
import eu.pretix.libpretixsync.models.db.toModel
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
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
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class AsyncCheckProvider(private val config: ConfigStore, private val dataStore: BlockingEntityStore<Persistable>, private val db: SyncDatabase) : TicketCheckProvider {
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

            // Re-fetch event/sub-event to get raw JSON and use date values from that
            // Should be less risky than converting back and forth between java.time and Joda
            val evjson = if (subeventId != 0L) {
                val jsonData = db.subEventQueries.selectByServerIdAndSlug(
                    server_id = subeventId,
                    event_slug = event.slug,
                ).executeAsOne().json_data

                JSONObject(jsonData)
            } else {
                val jsonData = db.eventQueries.selectById(event.id).executeAsOne().json_data
                JSONObject(jsonData)
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
            val questionJson = db.questionQueries.selectByServerId(q.serverId).executeAsOne().json_data!!

            if (!q.askDuringCheckIn && !q.showDuringCheckIn) {
                continue
            }
            var answer: String? = ""
            if (answerMap.containsKey(q.serverId)) {
                answer = answerMap[q.serverId]
                try {
                    answer = q.clean_answer(answer, q.options, false)
                    val jo = JSONObject()
                    jo.put("answer", answer)
                    jo.put("question", q.serverId)
                    if (q.askDuringCheckIn) {
                        givenAnswers.put(jo)
                    }
                    if (q.showDuringCheckIn) {
                        shownAnswers.add(TicketCheckProvider.QuestionAnswer(q, questionJson, answer))
                    }
                } catch (e: QuestionLike.ValidationException) {
                    answer = ""
                    if (q.askDuringCheckIn) {
                        askQuestions = true
                    }
                } catch (e: JSONException) {
                    answer = ""
                    if (q.askDuringCheckIn) {
                        askQuestions = true
                    }
                }
            } else {
                if (q.askDuringCheckIn) {
                    askQuestions = true
                }
            }
            if (q.askDuringCheckIn) {
                requiredAnswers.add(TicketCheckProvider.QuestionAnswer(q, questionJson, answer))
            }
        }

        return RSAResult(givenAnswers, requiredAnswers, shownAnswers, askQuestions)
    }

    private fun checkOfflineWithoutData(eventsAndCheckinLists: Map<String, Long>, ticketid: String, type: TicketCheckProvider.CheckInType, answers: List<Answer>?, nonce: String?, allowQuestions: Boolean): TicketCheckProvider.CheckResult {
        val dt = now()
        val events = db.eventQueries.selectBySlugList(eventsAndCheckinLists.keys.toList())
            .executeAsList()
            .map { it.toModel() }
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
        val list = db.checkInListQueries.selectByServerIdAndEventSlug(
            server_id = listId,
            event_slug = eventSlug,
        ).executeAsOneOrNull()?.toModel()
            ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not found", offline = true)

        val is_revoked = db.revokedTicketSecretQueries.countForSecret(ticketid).executeAsOne()
        if (is_revoked > 0) {
            storeFailedCheckin(eventSlug, listId, "revoked", ticketid, type, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.REVOKED, offline = true)
        }

        val is_blocked = db.blockedTicketSecretQueries.countBlockedForSecret(ticketid).executeAsOne()
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

        if (!list.allItems) {
            val is_in_list = db.checkInListQueries.checkIfItemIsInList(
                checkin_list_id = list.id,
                item_id = decoded.item,
            ).executeAsOne()
            if (is_in_list == 0L) {
                storeFailedCheckin(eventSlug, listId, "product", ticketid, type, subevent = decoded.subevent, nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.PRODUCT, offline = true)
            }
        }

        if (list.subEventId != null && list.subEventId > 0 && list.subEventId != decoded.subevent) {
            storeFailedCheckin(eventSlug, listId, "invalid", ticketid, type, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID, offline = true)
        }

        val item = db.itemQueries.selectByServerIdAndEventSlug(
            server_id = decoded.item,
            event_slug = eventSlug,
        ).executeAsOneOrNull()?.toModel()
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
                item.getVariation(decoded.variation!!)
            } catch (e: JSONException) {
                sentry.captureException(e)
                null
            }
        } else { null }
        if (variation != null) {
            res.variation = variation.stringValue
        }
        val require_attention = item.checkInAttention
        res.isRequireAttention = require_attention || (variation?.isCheckin_attention == true)
        res.checkinTexts = listOfNotNull(variation?.checkin_text?.trim(), item.checkInText?.trim()).filterNot { it.isBlank() }.filterNot { it.isBlank() || it == "null" }

        val queuedCheckIns = dataStore.select(QueuedCheckIn::class.java)
                .where(QueuedCheckIn.SECRET.eq(ticketid))
                .get().toList().filter {
                    it.getCheckinListId() == listId
                }.sortedWith(compareBy({ it.fullDatetime }, { it.id }))

        val rules = list.rules
        if (type == TicketCheckProvider.CheckInType.ENTRY && rules != null && rules.length() > 0) {
            val data = mutableMapOf<String, Any>()
            val tz = DateTimeZone.forID(event.timezone.toString())
            val jsonLogic = initJsonLogic(event, decoded.subevent ?: 0, tz)
            data.put("product", item.serverId.toString())
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

        val questions = db.questionQueries.selectForItem(item.id)
            .executeAsList()
            .map { it.toModel() }

        val answerMap = mutableMapOf<Long, String>()
        if (answers != null) {
            for (a in answers) {
                answerMap[(a.question as Question).serverId] = a.value
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
                            list.allowMultipleEntries ||
                            queuedCheckIns.isEmpty() ||
                            queuedCheckIns.all { it.type == "exit" } ||
                            (list.allowEntryAfterExit && queuedCheckIns.last().type == "exit")
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

        val tickets = db.orderPositionQueries.selectBySecretAndEventSlugs(
            secret = ticketid_cleaned,
            event_slugs = eventsAndCheckinLists.keys.toList(),
        ).executeAsList().map { it.toModel() }

        if (tickets.size == 0) {
            val medium = db.reusableMediumQueries.selectForCheck(
                identifier = ticketid_cleaned,
                type = source_type,
                event_slugs = eventsAndCheckinLists.keys.toList(),
            ).executeAsOneOrNull()?.toModel()
            if (medium != null) {
                val tickets = db.orderPositionQueries.selectByServerIdAndEventSlugs(
                    server_id = medium.linkedOrderPositionServerId,
                    event_slugs = eventsAndCheckinLists.keys.toList(),
                ).executeAsList().map { it.toModel() }
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
            val eventSlug = db.orderQueries.selectById(tickets[0].orderId).executeAsOne().event_slug!!
            val itemServerId = db.itemQueries.selectById(tickets[0].itemId).executeAsOne().server_id
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
                position = tickets[0].serverId,
                item = itemServerId,
                variation = tickets[0].variationServerId,
                subevent = tickets[0].subEventServerId,
                nonce = nonce,
            )
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.AMBIGUOUS)
        }
        return checkOfflineWithData(eventsAndCheckinLists, ticketid_cleaned, tickets, answers, ignore_unpaid, type, nonce = nonce, allowQuestions = allowQuestions)
    }

    private fun checkOfflineWithData(eventsAndCheckinLists: Map<String, Long>, secret: String, tickets: List<OrderPositionModel>, answers: List<Answer>?, ignore_unpaid: Boolean, type: TicketCheckProvider.CheckInType, nonce: String?, allowQuestions: Boolean): TicketCheckProvider.CheckResult {
        // !!! When extending this, also extend checkOfflineWithoutData !!!
        val dt = now()

        val order = db.orderQueries.selectById(tickets[0].orderId).executeAsOne().toModel()
        val item = db.itemQueries.selectById(tickets[0].itemId).executeAsOne().toModel()

        val eventSlug = order.eventSlug
        val event = db.eventQueries.selectBySlug(eventSlug).executeAsOneOrNull()?.toModel()

                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Event not found", offline = true)
        val listId = eventsAndCheckinLists[eventSlug] ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "No check-in list selected", offline = true)
        val list = db.checkInListQueries.selectByServerIdAndEventSlug(
            server_id = listId,
            event_slug = eventSlug,
        ).executeAsOneOrNull()?.toModel()
            ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not found", offline = true)

        val position = if (list.addonMatch) {
            // Add-on matching, as per spec, but only if we have data, it's impossible in data-less mode
            val candidates = mutableListOf(tickets[0])

            val positions = db.orderPositionQueries.selectForOrder(order.id).executeAsList().map { it.toModel() }
            candidates.addAll(positions.filter {
                it.addonToServerId == tickets[0].serverId
            })
            val filteredCandidates = if (!list.allItems) {
                val items = db.checkInListQueries.selectItemIdsForList(list.id)
                    .executeAsList()
                    .map {
                        // Not-null assertion needed for SQLite
                        it.id!!
                    }
                    .toHashSet()
                candidates.filter { candidate ->
                    val candidateItem = db.itemQueries.selectById(candidate.itemId).executeAsOne()
                    items.contains(candidateItem.id)
                }
            } else {
                // This is a useless configuration that the backend won't allow, but we'll still handle
                // it here for completeness
                candidates
            }
            if (filteredCandidates.isEmpty()) {
                storeFailedCheckin(eventSlug, list.serverId, "product", secret, type, position = tickets[0].serverId, item = item.serverId, variation = tickets[0].variationServerId, subevent = tickets[0].subEventServerId, nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.PRODUCT, offline = true)
            } else if (filteredCandidates.size > 1) {
                storeFailedCheckin(eventSlug, list.serverId, "ambiguous", secret, type, position = tickets[0].serverId, item = item.serverId, variation = tickets[0].variationServerId, subevent = tickets[0].subEventServerId, nonce = nonce)
                return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.AMBIGUOUS, offline = true)
            }
            filteredCandidates[0]
        } else {
            tickets[0]
        }

        val positionItem = if (position.id == tickets[0].id) {
            item
        } else {
            db.itemQueries.selectById(position.itemId).executeAsOne().toModel()
        }

        val jPosition: JSONObject
        jPosition = try {
            JSONObject(db.orderPositionQueries.selectById(position.id).executeAsOne().json_data)
        } catch (e: JSONException) {
            sentry.captureException(e)
            storeFailedCheckin(eventSlug, list.serverId, "error", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, offline = true)
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, offline = true)
        res.scanType = type
        res.ticket = positionItem.internalName
        val varid = position.variationServerId
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

        res.attendee_name = position.attendeeName
        res.seat = position.seatName
        res.orderCode = order.code
        res.positionId = position.positionId
        res.position = jPosition
        res.eventSlug = list.eventSlug
        var require_attention = order.requiresCheckInAttention
        try {
            require_attention = require_attention || item.checkInAttention
        } catch (e: JSONException) {
            sentry.captureException(e)
        }

        res.isRequireAttention = require_attention || variation?.isCheckin_attention == true
        res.checkinTexts = listOfNotNull(order.checkInText?.trim(), variation?.checkin_text?.trim(), item.checkInText?.trim()).filterNot { it.isBlank() || it == "null" }

        val storedCheckIns = db.checkInQueries.selectByPositionId(position.id).executeAsList().map { it.toModel() }
        val checkIns = storedCheckIns.filter {
            it.listServerId == listId
        }.sortedWith(compareBy({ it.fullDatetime }, { it.id }))

        if (order.status != OrderModel.Status.PAID && order.status != OrderModel.Status.PENDING) {
            res.type = TicketCheckProvider.CheckResult.Type.CANCELED
            res.isCheckinAllowed = false
            storeFailedCheckin(eventSlug, list.serverId, "canceled", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
            return res
        }

        if (position.blocked) {
            res.type = TicketCheckProvider.CheckResult.Type.BLOCKED
            res.isCheckinAllowed = false
            storeFailedCheckin(eventSlug, list.serverId, "blocked", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
            return res
        }

        if (order.status != OrderModel.Status.PAID && order.requiresApproval) {
            res.type = TicketCheckProvider.CheckResult.Type.UNAPPROVED
            res.isCheckinAllowed = false
            storeFailedCheckin(eventSlug, list.serverId, "unapproved", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
            return res
        }

        if (type != TicketCheckProvider.CheckInType.EXIT) {
            val validFrom = position.validFrom
            if (validFrom != null && validFrom.isAfter(javaTimeNow())) {
                res.type = TicketCheckProvider.CheckResult.Type.INVALID_TIME
                res.isCheckinAllowed = false
                storeFailedCheckin(eventSlug, list.serverId, "invalid_time", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
                return res
            }
            val validUntil = position.validUntil
            if (validUntil != null && validUntil.isBefore(javaTimeNow())) {
                res.type = TicketCheckProvider.CheckResult.Type.INVALID_TIME
                res.isCheckinAllowed = false
                storeFailedCheckin(eventSlug, list.serverId, "invalid_time", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
                return res
            }
        }

        if (!list.allItems) {
            val is_in_list = db.checkInListQueries.checkIfItemIsInList(
                checkin_list_id = list.id,
                item_id = item.id,
            ).executeAsOne()
            if (is_in_list == 0L) {
                storeFailedCheckin(eventSlug, list.serverId, "product", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
                res.type = TicketCheckProvider.CheckResult.Type.PRODUCT
                res.isCheckinAllowed = false
                return res
            }
        }

        if (list.subEventId != null && list.subEventId > 0 && list.subEventId != position.subEventServerId) {
            storeFailedCheckin(eventSlug, list.subEventId, "invalid", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
            return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.INVALID, offline = true)
        }

        if (!order.hasValidStatus && !(ignore_unpaid && list.includePending)) {
            res.type = TicketCheckProvider.CheckResult.Type.UNPAID
            res.isCheckinAllowed = list.includePending && !order.validIfPending
            storeFailedCheckin(eventSlug, list.serverId, "unpaid", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
            return res
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val rules = list.rules
        if (type == TicketCheckProvider.CheckInType.ENTRY && rules != null && rules.length() > 0) {
            val data = mutableMapOf<String, Any>()
            val tz = DateTimeZone.forID(event.timezone.toString())
            val jsonLogic = initJsonLogic(event, position.subEventServerId!!, tz)
            data.put("product", positionItem.serverId.toString())
            data.put("variation", position.variationServerId.toString())
            data.put("gate", config.deviceKnownGateID.toString())
            data.put("now", dt)
            data.put("now_isoweekday", dt.withZone(tz).dayOfWeek().get())
            data.put("entries_number", checkIns.filter { it.type == "entry" }.size)
            data.put("entries_today", checkIns.filter {
                it.fullDatetime.withZone(tz).toLocalDate() == dt.withZone(tz).toLocalDate() && it.type == "entry"
            }.size)
            data.put("entries_since", { cutoff: DateTime ->
                checkIns.filter {
                    it.fullDatetime.withZone(tz).isAfter(cutoff.minus(Duration.millis(1))) && it.type == "entry"
                }.size
            })
            data.put("entries_days_since", { cutoff: DateTime ->
                checkIns.filter {
                    it.fullDatetime.withZone(tz).isAfter(cutoff.minus(Duration.millis(1))) && it.type == "entry"
                }.map {
                    it.fullDatetime.withZone(tz).toLocalDate()
                }.toHashSet().size
            })
            data.put("entries_before", { cutoff: DateTime ->
                checkIns.filter {
                    it.fullDatetime.withZone(tz).isBefore(cutoff) && it.type == "entry"
                }.size
            })
            data.put("entries_days_before", { cutoff: DateTime ->
                checkIns.filter {
                    it.fullDatetime.withZone(tz).isBefore(cutoff) && it.type == "entry"
                }.map {
                    it.fullDatetime.withZone(tz).toLocalDate()
                }.toHashSet().size
            })
            data.put("entries_days", checkIns.filter { it.type == "entry" }.map {
                it.fullDatetime.withZone(tz).toLocalDate()
            }.toHashSet().size)
            val minutes_since_entries = checkIns.filter { it.type == "entry" }.map {
                Duration(it.fullDatetime.withZone(tz), dt).toStandardMinutes().minutes
            }
            data.put("minutes_since_last_entry", minutes_since_entries.minOrNull() ?: -1)
            data.put("minutes_since_first_entry", minutes_since_entries.maxOrNull() ?: -1)
            data.put("entry_status", if (checkIns.lastOrNull()?.type == "entry") {
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
                        list.serverId,
                        "rules",
                        position.secret!!,
                        type,
                        position = position.serverId,
                        item = positionItem.serverId,
                        variation = position.variationServerId,
                        subevent = position.subEventServerId,
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
                    list.serverId,
                    "rules",
                    position.secret!!,
                    type,
                    position = position.serverId,
                    item = positionItem.serverId,
                    variation = position.variationServerId,
                    subevent = position.subEventServerId,
                    nonce = nonce
                )
                return res
            }
        }

        // !!! When extending this, also extend checkOfflineWithoutData !!!

        val questions = db.questionQueries.selectForItem(item.id)
            .executeAsList()
            .map { it.toModel() }

        val answerMap = position.answers?.toMutableMap() ?: mutableMapOf()
        if (answers != null) {
            for (a in answers) {
                answerMap[(a.question as Question).serverId] = a.value
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
                            list.allowMultipleEntries ||
                            checkIns.isEmpty() ||
                            checkIns.all { it.type == "exit" } ||
                            (list.allowEntryAfterExit && checkIns.last().type == "exit")
                    )
            if (!entry_allowed) {
                res.isCheckinAllowed = false
                res.firstScanned = checkIns.first().fullDatetime.toDate()
                res.type = TicketCheckProvider.CheckResult.Type.USED
                storeFailedCheckin(eventSlug, list.serverId, "already_redeemed", position.secret!!, type, position = position.serverId, item = positionItem.serverId, variation = position.variationServerId, subevent = position.subEventServerId, nonce = nonce)
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
                db.checkInQueries.insert(
                    server_id = null,
                    listId = listId,
                    position = position.id,
                    type = type.toString().lowercase(Locale.getDefault()),
                    datetime = dt.toDate(),
                    json_data = "{\"local\": true, \"type\": \"${type.toString().lowercase(Locale.getDefault())}\", \"datetime\": \"${QueuedCheckIn.formatDatetime(dt.toDate())}\"}",
                )
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

        val eventFilter = mutableListOf<String>()
        val eventItemFilterEvents = mutableListOf<String>()
        val eventItemFilterItems = mutableListOf<Long>()
        val eventSubEventFilterEvents = mutableListOf<String>()
        val eventSubEventFilterSubEvents = mutableListOf<Long>()
        val allFilterEvents = mutableListOf<String>()
        val allFilterItems = mutableListOf<Long>()
        val allFilterSubEvents = mutableListOf<Long>()
        for (entry in eventsAndCheckinLists.entries) {
            val list = db.checkInListQueries.selectByServerIdAndEventSlug(
                server_id = entry.value,
                event_slug = entry.key,
            ).executeAsOneOrNull() ?: throw CheckException("Check-in list not found")

            val itemIds = if (!list.all_items) {
                db.checkInListQueries.selectItemIdsForList(list.id)
                    .executeAsList()
                    .map {
                        // Not-null assertion needed for SQLite
                        it.id!!
                    }
                    .ifEmpty { null }
            } else {
                null
            }

            val subEventId = if (list.subevent_id != null && list.subevent_id > 0) {
                list.subevent_id
            } else {
                null
            }

            if (itemIds != null && subEventId != null) {
                allFilterEvents.add(entry.key)
                allFilterItems.addAll(itemIds)
                allFilterSubEvents.add(subEventId)
            } else if (itemIds != null) {
                eventItemFilterEvents.add(entry.key)
                eventItemFilterItems.addAll(itemIds)
            } else if (subEventId != null) {
                eventSubEventFilterEvents.add(entry.key)
                eventSubEventFilterSubEvents.add(subEventId)
            } else {
                eventFilter.add(entry.key)
            }
        }

        // The individual filters need a separate flag, based on whether any of their lists are empty.
        // If any of them are, we also need to provide dummy values. These will not affect the
        // query result, but might still be evaluated.
        // All of this is done to avoid executing `<column> IN ()`, which is not valid SQL.
        // See https://github.com/sqldelight/sql-psi/issues/285
        // and https://www.postgresql.org/docs/current/sql-expressions.html#SYNTAX-EXPRESS-EVAL.
        val useEventFilter = if (eventFilter.isEmpty()) {
            eventFilter.add("")
            false
        } else {
            true
        }
        val useEventItemFilter = if (eventItemFilterEvents.isEmpty() || eventItemFilterItems.isEmpty()) {
            eventItemFilterEvents.add("")
            eventItemFilterItems.add(-1L)
            false
        } else {
            true
        }
        val useEventSubEventFilter = if (eventSubEventFilterEvents.isEmpty() || eventSubEventFilterSubEvents.isEmpty()) {
            eventSubEventFilterEvents.add("")
            eventSubEventFilterSubEvents.add(-1L)
            false
        } else {
            true
        }
        val useAllFilter = if (allFilterEvents.isEmpty() || allFilterItems.isEmpty() || allFilterSubEvents.isEmpty()) {
            allFilterEvents.add("")
            allFilterItems.add(-1L)
            allFilterSubEvents.add(-1L)
            false
        } else {
            true
        }

        val positions = db.orderPositionQueries.search(
            queryStartsWith = "$query%",
            queryContains = "%$query%",
            use_event_filter = useEventFilter,
            event_filter = eventFilter,
            use_event_item_filter = useEventItemFilter,
            event_item_filter_events = eventItemFilterEvents,
            event_item_filter_items = eventItemFilterItems,
            use_event_subevent_filter = useEventSubEventFilter,
            event_subevent_filter_events = eventSubEventFilterEvents,
            event_subevent_filter_subevents = eventSubEventFilterSubEvents,
            use_all_filter = useAllFilter,
            all_filter_events = allFilterEvents,
            all_filter_items = allFilterItems,
            all_filter_subevents = allFilterSubEvents,
            limit = 50L,
            offset = 50L * (page - 1L),
        )
            .executeAsList()
            .map { it.toModel() }

        // TODO: search invoice_address?
        for (position in positions) {
            val order = db.orderQueries.selectById(position.orderId).executeAsOne().toModel()
            val item = db.itemQueries.selectById(position.itemId).executeAsOne().toModel()
            val sr = TicketCheckProvider.SearchResult()
            sr.ticket = item.internalName
            val variation = try {
                if (position.variationServerId != null && position.variationServerId > 0) {
                    item.getVariation(position.variationServerId)
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
            sr.attendee_name = position.attendeeName
            sr.seat = position.seatName
            sr.orderCode = order.code
            sr.positionId = position.positionId
            sr.secret = position.secret

            val queuedCheckIns = dataStore.count(QueuedCheckIn::class.java)
                    .where(QueuedCheckIn.SECRET.eq(position.secret))
                    .and(QueuedCheckIn.CHECKIN_LIST_ID.`in`(eventsAndCheckinLists.values.toList()))
                    .get().value().toLong()
            val checkIns = db.checkInQueries.selectByPositionId(position.id).executeAsList().map { it.toModel() }
            var is_checked_in = queuedCheckIns > 0
            for (ci in checkIns) {
                if (eventsAndCheckinLists.containsValue(ci.listServerId)) {
                    is_checked_in = true
                    break
                }
            }
            sr.isRedeemed = is_checked_in
            if (order.status == OrderModel.Status.PAID) {
                sr.status = TicketCheckProvider.SearchResult.Status.PAID
            } else if (order.status == OrderModel.Status.PENDING) {
                sr.status = TicketCheckProvider.SearchResult.Status.PENDING
            } else {
                sr.status = TicketCheckProvider.SearchResult.Status.CANCELED
            }
            var require_attention = order.requiresCheckInAttention
            try {
                require_attention = require_attention || item.checkInAttention || variation?.isCheckin_attention == true
            } catch (e: JSONException) {
                sentry.captureException(e)
            }
            sr.isRequireAttention = require_attention
            sr.position = JSONObject(db.orderPositionQueries.selectById(position.id).executeAsOne().json_data)
            results.add(sr)
        }
        return results
    }

    private fun basePositionQuery(lists: List<CheckInList>, onlyCheckedIn: Boolean): WhereAndOr<out Scalar<Int?>?> {

        var q = dataStore.count(OrderPosition::class.java).distinct()
                .leftJoin(Order::class.java).on(OrderPosition.ORDER_ID.eq(Order.ID))
                .where(OrderPosition.SERVER_ID.eq(-1))  // stupid logic node just so we can dynamically add .or() below

        for (list in lists) {
            var lq: Logical<*, *> = Order.EVENT_SLUG.eq(list.eventSlug)
            if (list.includePending) {
                lq = lq.and(Order.STATUS.`in`(listOf("p", "n")))
            } else {
                lq = lq.and(Order.STATUS.eq("p").or(Order.STATUS.eq("n").and(Order.VALID_IF_PENDING.eq(true))))
            }

            if (list.subEventId != null && list.subEventId > 0) {
                lq = lq.and(OrderPosition.SUBEVENT_ID.eq(list.subEventId))
            }

            if (!list.allItems) {
                val product_ids = db.checkInListQueries.selectItemIdsForList(list.id)
                    .executeAsList()
                    .map {
                        // Not-null assertion needed for SQLite
                        it.id!!
                    }
                lq = lq.and(OrderPosition.ITEM_ID.`in`(product_ids))
            }

            if (onlyCheckedIn) {
                lq = lq.and(OrderPosition.ID.`in`(
                        db.checkInQueries.selectPositionIdByListIdAndType(
                            list_server_id = list.serverId,
                            type = "entry"
                        ).executeAsList().map { it.position }
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
        val list = db.checkInListQueries.selectByServerIdAndEventSlug(
            server_id = listId,
            event_slug = eventSlug,
        ).executeAsOneOrNull()?.toModel()
            ?: throw CheckException("Check-in list not found")

        val products = if (list.allItems) {
            db.itemQueries.selectByEventSlug(eventSlug)
                .executeAsList()
                .map { it.toModel() }
        } else {
            db.itemQueries.selectForCheckInList(list.id)
                .executeAsList()
                .map { it.toModel() }
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
                        product.serverId,
                        product.internalName,
                        position_count,
                        ci_count,
                        variations,
                        product.admission
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

    private fun javaTimeNow(): OffsetDateTime {
        val jodaNow = now()
        val instant = Instant.ofEpochMilli(jodaNow.millis)
        val zoneId = jodaNow.zone.toTimeZone().toZoneId()
        return OffsetDateTime.ofInstant(instant, zoneId)
    }

    private val CheckIn.fullDatetime : DateTime
        get() {
            // To avoid Joda Time code in the models, handle the case where we don't have a datetime value from JSON here
            return if (this.datetime != null) {
                DateTime(this.datetime.toInstant().toEpochMilli())
            } else {
                val date = db.checkInQueries.selectById(this.id).executeAsOne().datetime
                DateTime(date)
            }
        }
}
