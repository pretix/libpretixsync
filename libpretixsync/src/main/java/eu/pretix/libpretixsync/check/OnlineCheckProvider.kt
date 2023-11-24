package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.TimeoutApiException
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.CheckInList
import eu.pretix.libpretixsync.db.Item
import eu.pretix.libpretixsync.db.NonceGenerator
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

class OnlineCheckProvider(
    private val config: ConfigStore,
    httpClientFactory: HttpClientFactory?,
    private val dataStore: BlockingEntityStore<Persistable>,
    private val fileStore: FileStorage,
    private val fallback: TicketCheckProvider? = null,
    private val fallbackTimeout: Int = 30000
) : TicketCheckProvider {
    private var sentry: SentryInterface = DummySentryImplementation()
    private val api = PretixApi.fromConfig(config, httpClientFactory)
    private val parser = ISODateTimeFormat.dateTimeParser()

    override fun setSentry(sentry: SentryInterface) {
        this.sentry = sentry
        api.sentry = sentry
    }

    override fun check(
        eventsAndCheckinLists: Map<String, Long>,
        ticketid: String,
        source_type: String,
        answers: List<Answer>?,
        ignore_unpaid: Boolean,
        with_badge_data: Boolean,
        type: TicketCheckProvider.CheckInType,
        nonce: String?
    ): TicketCheckProvider.CheckResult {
        val ticketid_cleaned = ticketid.replace(Regex("[\\p{C}]"), "�")  // remove unprintable characters
        val nonce_cleaned = nonce ?: NonceGenerator.nextNonce()

        sentry.addBreadcrumb("provider.check", "started")
        return try {
            val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
            res.scanType = type
            val responseObj = if (config.knownPretixVersion >= 40120001001) { // >= 4.12.0.dev1
                api.redeem(
                    eventsAndCheckinLists.values.toList(),
                    ticketid_cleaned,
                    null as String?,
                    false,
                    nonce_cleaned,
                    answers,
                    ignore_unpaid,
                    with_badge_data,
                    type.toString().lowercase(Locale.getDefault()),
                    callTimeout = if (fallback != null) fallbackTimeout.toLong() else null,
                )
            } else {
                if (eventsAndCheckinLists.size != 1) throw CheckException("Multi-event scan not supported by server.")
                api.redeem(
                    eventsAndCheckinLists.keys.first(),
                    ticketid_cleaned,
                    null as String?,
                    false,
                    nonce_cleaned,
                    answers,
                    eventsAndCheckinLists.values.first(),
                    ignore_unpaid,
                    with_badge_data,
                    type.toString().lowercase(Locale.getDefault()),
                    source_type,
                    callTimeout = if (fallback != null) fallbackTimeout.toLong() else null,
                )
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
                    val required_answers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
                    for (i in 0 until response.getJSONArray("questions").length()) {
                        val q = response.getJSONArray("questions").getJSONObject(i)
                        val question = Question()
                        question.setServer_id(q.getLong("id"))
                        question.isRequired = q.getBoolean("required")
                        question.setPosition(q.getLong("position"))
                        question.setJson_data(q.toString())
                        required_answers.add(TicketCheckProvider.QuestionAnswer(question, ""))
                    }
                    res.requiredAnswers = required_answers
                } else {
                    val reason = response.optString("reason")
                    if ("already_redeemed" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.USED
                    } else if ("unknown_ticket" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.INVALID
                    } else if ("invalid_time" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.INVALID_TIME
                    } else if ("blocked" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.BLOCKED
                    } else if ("canceled" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.CANCELED
                    } else if ("rules" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.RULES
                    } else if ("ambiguous" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.AMBIGUOUS
                    } else if ("revoked" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.REVOKED
                    } else if ("unapproved" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.UNAPPROVED
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
                    } else {
                        res.type = TicketCheckProvider.CheckResult.Type.ERROR
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

                    if (posjson.has("answers") && posjson.getJSONArray("answers").length() > 0) {
                        val shownAnswers: MutableList<TicketCheckProvider.QuestionAnswer> = ArrayList()
                        for (i in 0 until (posjson.getJSONArray("answers").length())) {
                            val a = posjson.getJSONArray("answers").getJSONObject(i)
                            val value = a.getString("answer")
                            val q = a.get("question")
                            if (q is JSONObject) {  // pretix version supports the expand parameter
                                val question = Question()
                                question.setServer_id(q.getLong("id"))
                                question.isRequired = q.getBoolean("required")
                                question.setPosition(q.getLong("position"))
                                question.setJson_data(q.toString())
                                if (question.isShowDuringCheckin) {
                                    shownAnswers.add(
                                        TicketCheckProvider.QuestionAnswer(
                                            question,
                                            value
                                        )
                                    )
                                }
                            }
                        }
                        res.shownAnswers = shownAnswers
                    }
                }

                res.isRequireAttention = response.optBoolean("require_attention", false)
                if (response.has("checkin_texts")) {
                    val checkinTexts = response.getJSONArray("checkin_texts")
                    res.checkinTexts = List(checkinTexts.length()) {
                        checkinTexts.getString(it)
                    }
                }
            }
            res
        } catch (e: JSONException) {
            sentry.captureException(e)
            val cr = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Invalid server response")
            if (e.cause != null) cr.ticket = e.cause!!.message
            cr
        } catch (e: ApiException) {
            if (e is TimeoutApiException && fallback != null && nonce_cleaned != null) {
                /*
                We are in the following situation: The user configured the app to automatically decide between
                online and offline mode, and the user has decided that they want to switch to offline mode if
                scans take longer than `fallbackTimeout`.

                With this fallback option, we allow to enforce this timeout even on the very first scan:
                If we scan a ticket and the server does not respond within `fallbackTimeout`, we re-try
                the same scan using our fallback method ("offline scan").

                Now, the following scenarios are relevant:

                1) Our original scan attempt has never reached the server or has never been processed there.
                   Therefore, our offline scan will be the only scan recorded. This is acceptable, since
                   it's not worse than if the scanner would have auto-switched to offline mode a minute ago.

                2) Our original scan attempt has been processed on the server but we never received the
                   response. The outcome depends on the processing result:

                   a) The scan was successful both on the server as well as with our fallback method:
                      Everything is fine. Since both scans carry the same "nonce", only one scan will
                      be in the database since the server ignores our upload of the offline scan later.

                   b) The scan failed on the server as well as with our fallback method:
                      Everything is fine. Since both scans carry the same "nonce", only one scan will
                      be in the database since the server ignores our upload of the offline scan later.

                   c) The scan failed on the server, but our fallback method recorded a successful scan:
                      This is not great, because someone got in who shouldn't have, but it's no worse than
                      if the scanner would have auto-switched to offline mode a minute ago, so it's
                      acceptable by any user who configured our app like this. The database will later
                      show both a failed and a successful scan (since nonces of failed scans do not block
                      further scans), but that's also the most sensible description of what happened to
                      a user who looks at the process in the backend, so we consider it acceptable. It's
                      also no different to a user manually quickly retrying the scan after a failure.

                   d) The scan succeeded on the server, but failed with our fallback method. This is the
                      worst situation since a valid scan is recorded and the ticket is now marked as used,
                      even though the ticket holder did not get in. This risk always existed as it can
                      happen in any situation where the request reached the server but the response did
                      not reach the scanner, but it becomes a more likely situation with a lower timeout.
                 */
                fallback.check(
                    eventsAndCheckinLists,
                    ticketid_cleaned,
                    source_type,
                    answers,
                    ignore_unpaid,
                    with_badge_data,
                    type,
                    nonce_cleaned
                )
            } else {
                sentry.addBreadcrumb("provider.check", "API Error: " + e.message)
                val cr = TicketCheckProvider.CheckResult(
                    TicketCheckProvider.CheckResult.Type.ERROR,
                    e.message
                )
                if (e.cause != null) cr.ticket = e.cause!!.message
                cr
            }
        }
    }

    override fun check(eventsAndCheckinLists: Map<String, Long>, ticketid: String): TicketCheckProvider.CheckResult {
        return check(eventsAndCheckinLists, ticketid, "barcode", ArrayList(), false, true, TicketCheckProvider.CheckInType.ENTRY)
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