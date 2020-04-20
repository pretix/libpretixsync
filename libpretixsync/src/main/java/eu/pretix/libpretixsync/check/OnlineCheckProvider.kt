package eu.pretix.libpretixsync.check

import eu.pretix.libpretixsync.DummySentryImplementation
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.DefaultHttpClientFactory
import eu.pretix.libpretixsync.api.HttpClientFactory
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.db.CheckInList
import eu.pretix.libpretixsync.db.Item
import eu.pretix.libpretixsync.db.Question
import io.requery.BlockingEntityStore
import io.requery.Persistable
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class OnlineCheckProvider(private val config: ConfigStore, httpClientFactory: HttpClientFactory?, dataStore: BlockingEntityStore<Persistable?>, listId: Long) : TicketCheckProvider {
    protected var api: PretixApi
    private var sentry: SentryInterface
    private val dataStore: BlockingEntityStore<Persistable?>
    private val listId: Long
    private val parser = ISODateTimeFormat.dateTimeParser()

    constructor(config: ConfigStore, dataStore: BlockingEntityStore<Persistable?>, listId: Long) : this(config, DefaultHttpClientFactory(), dataStore, listId) {}

    fun getSentry(): SentryInterface {
        return sentry
    }

    override fun setSentry(sentry: SentryInterface) {
        this.sentry = sentry
        api.sentry = sentry
    }

    override fun check(ticketid: String, answers: List<TicketCheckProvider.Answer>?, ignore_unpaid: Boolean, with_badge_data: Boolean): TicketCheckProvider.CheckResult {
        sentry.addBreadcrumb("provider.check", "started")
        val list = dataStore.select(CheckInList::class.java)
                .where(CheckInList.SERVER_ID.eq(listId))
                .and(CheckInList.EVENT_SLUG.eq(config.eventSlug))
                .get().firstOrNull()
                ?: return TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR, "Check-in list not found")
        return try {
            val res = TicketCheckProvider.CheckResult(TicketCheckProvider.CheckResult.Type.ERROR)
            val responseObj = api.redeem(ticketid, null as String?, false, null, answers, listId, ignore_unpaid, with_badge_data)
            if (responseObj.response.code == 404) {
                res.type = TicketCheckProvider.CheckResult.Type.INVALID
            } else {
                val response = responseObj.data
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
                    } else if ("unpaid" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.UNPAID
                        // Decide whether the user is allowed to "try again" with "ignore_unpaid"
                        res.isCheckinAllowed = list.include_pending && response.has("position") && response.getJSONObject("position").optString("order__status", "n") == "n"
                    } else if ("product" == reason) {
                        res.type = TicketCheckProvider.CheckResult.Type.PRODUCT
                    }
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
                    res.orderCode = posjson.optString("order")
                    res.position = posjson
                    val checkins = posjson.getJSONArray("checkins")
                    for (i in 0 until checkins.length()) {
                        val ci = checkins.getJSONObject(i)
                        if (ci.getLong("list") == listId) {
                            res.firstScanned = parser.parseDateTime(ci.getString("datetime")).toDate()
                        }
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

    override fun check(ticketid: String): TicketCheckProvider.CheckResult {
        return check(ticketid, ArrayList(), false, true)
    }

    @Throws(CheckException::class)
    override fun search(query: String, page: Int): List<TicketCheckProvider.SearchResult> {
        sentry.addBreadcrumb("provider.search", "started")
        return try {
            val response = api.search(listId, query, page)
            val resdata = response.data.getJSONArray("results")
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
                sr.orderCode = res.optString("order")
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
                results.add(sr)
            }
            results
        } catch (e: JSONException) {
            sentry.captureException(e)
            throw CheckException("Unknown server response")
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.message)
            throw CheckException(e.message)
        }
    }

    @Throws(CheckException::class)
    override fun status(): TicketCheckProvider.StatusResult {
        sentry.addBreadcrumb("provider.status", "started")
        return try {
            val response = api.status(listId)
            parseStatusResponse(response.data)
        } catch (e: JSONException) {
            sentry.captureException(e)
            throw CheckException("Unknown server response")
        } catch (e: ApiException) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.message)
            throw CheckException(e.message)
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
                    items
            )
        }
    }

    init {
        api = PretixApi.fromConfig(config, httpClientFactory)
        sentry = DummySentryImplementation()
        this.listId = listId
        this.dataStore = dataStore
    }
}