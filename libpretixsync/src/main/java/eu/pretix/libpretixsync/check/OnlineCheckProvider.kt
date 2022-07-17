package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.CheckInList
import eu.pretix.libpretixsync.db.Item
import eu.pretix.libpretixsync.db.Question
import eu.pretix.libpretixsync.sync.FileStorage
import eu.pretix.libpretixsync.sync.OrderSyncAdapter
import io.requery.BlockingEntityStore
import io.requery.Persistable
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
import java.util.*

class OnlineCheckProvider(private val config: ConfigStore, httpClientFactory: HttpClientFactory?, private val dataStore: BlockingEntityStore<Persistable>, private val fileStore: FileStorage) : TicketCheckProvider {
    private var sentry: SentryInterface = DummySentryImplementation()
    private val api = PretixApi.fromConfig(config, httpClientFactory)
    private val parser = ISODateTimeFormat.dateTimeParser()

    override fun setSentry(sentry: SentryInterface) {
        this.sentry = sentry
        api.sentry = sentry
    }

    override fun check(eventsAndCheckinLists: Map<String, Long>, ticketid_: String, answers: List<Answer>?, ignore_unpaid: Boolean, with_badge_data: Boolean, type: TicketCheckProvider.CheckInType): TicketCheckProvider.CheckResult {
        val ticketid = ticketid_.replace(Regex("[\\p{C}]"), "�")  // remove unprintable characters

        sentry.addBreadcrumb("provider.check", "started")
        return try {
            val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
            res.scanType = type
            val responseObj = if (config.knownPretixVersion >= 40120001001) { // >= 4.12.0.dev1
                api.redeem(eventsAndCheckinLists.values.toList(), ticketid, null as String?, false, null, answers, ignore_unpaid, with_badge_data, type.toString().lowercase(Locale.getDefault()))
            } else {
                if (eventsAndCheckinLists.size != 1) throw CheckException("Multi-event scan not supported by server.")
                api.redeem(eventsAndCheckinLists.keys.first(), ticketid, null as String?, false, null, answers, eventsAndCheckinLists.values.first(), ignore_unpaid, with_badge_data, type.toString().lowercase(Locale.getDefault()))
            }
            if (responseObj.response.code == 404) {
                res.type = TicketCheckProvider.CheckResult.Type.INVALID
            } else {
                val response = responseObj.data!!
                val status = response.getString("status")
                if ("ok" == status) {
                    res.type = TicketCheckProvider.CheckResult.Type.VALID
                } else if ("incomplete" == status) {
                    res.type = TicketCheckProvider.CheckResult.Type.ANSWERS_REQUIRED
                    val required_answers: MutableList<TicketCheckProvider.RequiredAnswer> = ArrayList()
                    for (i in 0 until response.getJSONArray("questions").length()) {
                        val q = response.getJSONArray("questions").getJSONObject(i)
                        val question = Question()
                        question.setServer_id(q.getLong("id"))
                        question.isRequired = q.getBoolean("required")
                        question.setPosition(q.getLong("position"))
                        question.setJson_data(q.toString())
                        required_answers.add(TicketCheckProvider.RequiredAnswer(question, ""))
                    }
                    res.requiredAnswers = required_answers
                } else {
                    val reason = response.optString("reason")
                    if ("already_redeemed" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.USED
                    } else if ("unknown_ticket" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.INVALID
                    } else if ("canceled" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.CANCELED
                    } else if ("rules" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.RULES
                    } else if ("ambiguous" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.AMBIGUOUS
                    } else if ("revoked" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.REVOKED
                    } else if ("unpaid" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.UNPAID
                        // Decide whether the user is allowed to "try again" with "ignore_unpaid"

                        val includePending = if (response.has("list")) {
                            // pretix >= 4.12
                            response.getJSONObject("list").getBoolean("include_pending")
                        } else {
                            // pretix < 4.12, no multi-scan supported
                            val list = dataStore.select(CheckInList::class.java)
                                    .where(CheckInList.SERVER_ID.eq(eventsAndCheckinLists.values.first()))
                                    .and(CheckInList.EVENT_SLUG.eq(eventsAndCheckinLists.keys.first()))
                                    .get().firstOrNull()
                                    ?: throw CheckException("Check-in list not found")
                            list.isInclude_pending
                        }
                        res.isCheckinAllowed = includePending && response.has("position") && response.getJSONObject("position").optString("order__status", "n") == "n"
                    } else if ("product" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.PRODUCT
                    }
                    if (response.has("reason_explanation") && !response.isNull("reason_explanation")) {
                        res.reasonExplanation = response.getString("reason_explanation")
                    }
                }

                if (response.has("list")) {
                    // pretix >= 4.12
                    res.eventSlug = response.getJSONObject("list").getString("event")
                } else {
                    res.eventSlug = eventsAndCheckinLists.keys.first()
                }

                if (response.has("position")) {
                    val posjson = response.getJSONObject("position")
                    val item = dataStore.select(Item::class.java)
                            .where(Item.SERVER_ID.eq(posjson.getLong("item")))
                            .get().firstOrNull()
                    if (item != null) {
                        res.ticket = item.internalName
                        if (posjson.optLong("variation", 0) > 0) {
                            val iv = item.getVariation(posjson.getLong("variation"))
                            if (iv != null) {
                                res.variation = iv.stringValue
                            }
                        }
                    }
                    if (!posjson.isNull("attendee_name")) {
                        res.attendee_name = posjson.optString("attendee_name")
                    }
                    if (!posjson.isNull("seat")) {
                        res.seat = posjson.getJSONObject("seat").getString("name")
                    }
                    res.orderCode = posjson.optString("order")
                    res.positionId = posjson.optLong("positionid")
                    res.position = posjson
                    val checkins = posjson.getJSONArray("checkins")
                    for (i in 0 until checkins.length()) {
                        val ci = checkins.getJSONObject(i)
                        if (eventsAndCheckinLists.containsValue(ci.getLong("list"))) {
                            res.firstScanned = parser.parseDateTime(ci.getString("datetime")).toDate()
                        }
                    }

                    // Images
                    try {
                        if (posjson.has("pdf_data")) {
                            val pdfdata = posjson.getJSONObject("pdf_data")
                            if (pdfdata.has("images")) {
                                val images = pdfdata.getJSONObject("images")
                                OrderSyncAdapter.updatePdfImages(dataStore, fileStore, api, posjson.getLong("id"), images)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // ignore, we don't want the whole thing to fail because of this
                    }

                }
                res.isRequireAttention = response.optBoolean("require_attention", false)
            }
            res
        } catch (e: JSONException) {
            sentry.captureException(e)
            val cr = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Invalid server response")
            if (e.cause != null) cr.ticket = e.cause!!.message
            cr
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.check", "API Error: " + e.message)
            val cr = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, e.message)
            if (e.cause != null) cr.ticket = e.cause!!.message
            cr
        }
    }

    override fun check(eventsAndCheckinLists: Map<String, Long>, ticketid: String): TicketCheckProvider.CheckResult {
        return check(eventsAndCheckinLists, ticketid, ArrayList(), false, true, TicketCheckProvider.CheckInType.ENTRY)
    }

    @Throws(CheckException::class)
    override fun search(eventsAndCheckinLists: Map<String, Long>, query: String, page: Int): List<TicketCheckProvider.SearchResult> {
        sentry.addBreadcrumb("provider.search", "started")
        return try {
            val response = if (config.knownPretixVersion >= 40120001001) { // < 4.12.0.dev1
                api.search(eventsAndCheckinLists.values.toList(), query, page)
            } else {
                if (eventsAndCheckinLists.size != 1) throw CheckException("Multi-event scan not supported by server.")
                api.search(eventsAndCheckinLists.keys.first(), eventsAndCheckinLists.values.first(), query, page)
            }
            val resdata = response.data!!.getJSONArray("results")
            val results: MutableList<TicketCheckProvider.SearchResult> = ArrayList()
            for (i in 0 until resdata.length()) {
                val res = resdata.getJSONObject(i)
                val sr = TicketCheckProvider.SearchResult()
                val item = dataStore.select(Item::class.java)
                        .where(Item.SERVER_ID.eq(res.getLong("item")))
                        .get().firstOrNull()
                if (item != null) {
                    sr.ticket = item.internalName
                    if (res.optLong("variation", 0) > 0) {
                        val iv = item.getVariation(res.getLong("variation"))
                        if (iv != null) {
                            sr.variation = iv.stringValue
                        }
                    }
                }
                if (!res.isNull("attendee_name")) {
                    sr.attendee_name = res.optString("attendee_name")
                }
                if (!res.isNull("seat")) {
                    sr.seat = res.getJSONObject("seat").getString("name")
                }
                sr.orderCode = res.optString("order")
                sr.positionId = res.optLong("positionid")
                sr.secret = res.optString("secret")
                sr.isRedeemed = res.getJSONArray("checkins").length() > 0
                val status = res.optString("order__status", "p")
                if (status == "p") {
                    sr.status = TicketCheckProvider.SearchResult.Status.PAID
                } else if (status == "n") {
                    sr.status = TicketCheckProvider.SearchResult.Status.PENDING
                } else {
                    sr.status = TicketCheckProvider.SearchResult.Status.CANCELED
                }
                sr.isRequireAttention = res.optBoolean("require_attention", false)
                sr.position = res
                results.add(sr)
            }
            results
        } catch (e: JSONException) {
            sentry.captureException(e)
            throw CheckException("Unknown server response", e)
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.message)
            throw CheckException(e.message, e)
        }
    }

    @Throws(CheckException::class)
    override fun status(eventSlug: String, listId: Long): TicketCheckProvider.StatusResult? {
        sentry.addBreadcrumb("provider.status", "started")
        return try {
            val response = api.status(eventSlug, listId)
            val r = parseStatusResponse(response.data!!)

            val list = dataStore.select(CheckInList::class.java)
                    .where(CheckInList.SERVER_ID.eq(listId))
                    .and(CheckInList.EVENT_SLUG.eq(eventSlug))
                    .get().firstOrNull()
            if (list != null) {
                r.eventName += " – " + list.name
            }

            r
        } catch (e: JSONException) {
            sentry.captureException(e)
            throw CheckException("Unknown server response", e)
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.message)
            throw CheckException(e.message, e)
        }
    }

    companion object {
        @Throws(JSONException::class)
        fun parseStatusResponse(response: JSONObject): TicketCheckProvider.StatusResult {
            val items: MutableList<TicketCheckProvider.StatusResultItem> = ArrayList()
            val itemcount = response.getJSONArray("items").length()
            for (i in 0 until itemcount) {
                val item = response.getJSONArray("items").getJSONObject(i)
                val variations: MutableList<TicketCheckProvider.StatusResultItemVariation> = ArrayList()
                val varcount = item.getJSONArray("variations").length()
                for (j in 0 until varcount) {
                    val `var` = item.getJSONArray("variations").getJSONObject(j)
                    variations.add(TicketCheckProvider.StatusResultItemVariation(
                            `var`.getLong("id"),
                            `var`.getString("value"),
                            `var`.getInt("position_count"),
                            `var`.getInt("checkin_count")
                    ))
                }
                items.add(TicketCheckProvider.StatusResultItem(
                        item.getLong("id"),
                        item.getString("name"),
                        item.getInt("position_count"),
                        item.getInt("checkin_count"),
                        variations,
                        item.getBoolean("admission")
                ))
            }
            return TicketCheckProvider.StatusResult(
                    response.getJSONObject("event").getString("name"),
                    response.getInt("position_count"),
                    response.getInt("checkin_count"),
                    if (response.has("inside_count")) response.optInt("inside_count") else null,
                    items
            )
        }
    }
}