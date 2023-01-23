package eu.pretix.libpretixsync.setup

import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.config.ConfigStore
import eu.pretix.libpretixsync.utils.I18nString
import io.requery.BlockingEntityStore
import io.requery.Persistable
import org.joda.time.DateTime
import org.joda.time.Hours
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class EventManager(private val store: BlockingEntityStore<Persistable>, private val api: PretixApi, private val conf: ConfigStore, private val require_live: Boolean) {
    val eventMap = HashMap<String, PretixApi.ApiResponse>()

    fun getAvailableEvents() : List<RemoteEvent> {
        val oneDayAgo = DateTime.now() - Hours.hours(24)
        return getAvailableEvents(oneDayAgo, 5, null, null, null)
    }

    fun getAvailableEvents(endsAfter: DateTime, maxPages: Int, availabilityForChannel: String?, requireChannel: String?, searchQuery: String?) : List<RemoteEvent> {
        eventMap.clear()

        val avail = if (availabilityForChannel != null) "with_availability_for=${availabilityForChannel}&" else ""

        val endsAfterUrl = URLEncoder.encode(endsAfter.toString())

        var eventsUrl = api.organizerResourceUrl("events") + "?${avail}ends_after=$endsAfterUrl&ordering=date_from"
        if (require_live) eventsUrl += "&live=true"
        if (requireChannel != null) eventsUrl += "&sales_channel=$requireChannel"
        if (!searchQuery.isNullOrBlank()) eventsUrl += "&search=" + URLEncoder.encode(searchQuery)

        val resp_events = api.fetchResource(eventsUrl)
        if (resp_events.response.code != 200) {
            throw IOException()
        }
        var events = parseEvents(resp_events.data!!, maxDepth=maxPages)

        var subeventsUrl = api.organizerResourceUrl("subevents") + "?${avail}ends_after=$endsAfterUrl&ordering=date_from&active=true"
        if (require_live) subeventsUrl += "&event__live=true"
        if (requireChannel != null) subeventsUrl += "&sales_channel=$requireChannel"
        if (!searchQuery.isNullOrBlank()) subeventsUrl += "&search=" + URLEncoder.encode(searchQuery)

        val resp_subevents = api.fetchResource(subeventsUrl)
        if (resp_subevents.response.code != 200) {
            throw IOException()
        }
        events += parseEvents(resp_subevents.data!!, subevents=true, maxDepth=maxPages)

        return events.sortedBy {
            return@sortedBy it.date_from
        }
    }

    private fun parseEvents(data: JSONObject, maxDepth: Int, subevents: Boolean = false, depth: Int=1): List<RemoteEvent> {
        val events = ArrayList<RemoteEvent>()

        val results = data.getJSONArray("results")
        for (i in 0 until results.length()) {
            val json = results.getJSONObject(i)
            events.add(RemoteEvent(
                    name_i18n=json.getJSONObject("name"),
                    slug=if (subevents) json.getString("event") else json.getString("slug"),
                    date_from=DateTime(json.getString("date_from")),
                    date_to=if (json.isNull("date_to")) null else {
                        DateTime(json.optString("date_to"))
                    },
                    subevent_id=if (subevents) {
                        json.getLong("id")
                    } else null,
                    best_availability_state=if(json.has("best_availability_state") && !json.isNull("best_availability_state")) {
                        json.getLong("best_availability_state")
                    } else {
                        null
                    },
                    live=if (subevents && !require_live) {
                        val eventSlug = json.getString("event")
                        var event = eventMap[eventSlug]
                        if (event == null) {
                            event = api.fetchResource(api.organizerResourceUrl("events/" + eventSlug))

                            if (event.response.code != 200) {
                                throw IOException()
                            }
                            eventMap[eventSlug] = event
                        }

                        event.data!!.getBoolean("live") && json.getBoolean("active")
                    } else if (require_live) { true } else {
                        json.getBoolean("live")
                    }))
        }


        if (!data.isNull("next") && depth < maxDepth) {
            val next = data.getString("next")
            val resp = api.fetchResource(next)
            if (resp.response.code != 200) {
                throw IOException()
            }
            return events + parseEvents(resp.data!!, maxDepth, subevents, depth + 1)
        } else {
            return events
        }
    }
}

data class RemoteEvent(
        val name_i18n: JSONObject,
        val slug: String,
        val date_from: DateTime,
        val date_to: DateTime?,
        val subevent_id: Long?,
        val best_availability_state: Long?,
        val live: Boolean
) {
    val name: String
        get() = I18nString.toString(name_i18n)
}
