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
        eventMap.clear()

        val oneDayAgo = URLEncoder.encode((DateTime.now() - Hours.hours(24)).toString())

        val resp_events = api.fetchResource(api.organizerResourceUrl("events") +
                "?ends_after=$oneDayAgo" + if (require_live) "&live=true" else "")
        if (resp_events.response.code != 200) {
            throw IOException()
        }
        var events = parseEvents(resp_events.data!!)

        val resp_subevents = api.fetchResource(api.organizerResourceUrl("subevents")
                + "?ends_after=$oneDayAgo" + if (require_live) "&active=true&event__live=true" else "")
        if (resp_subevents.response.code != 200) {
            throw IOException()
        }
        events += parseEvents(resp_subevents.data!!, subevents=true)

        return events.sortedBy {
            return@sortedBy it.date_from
        }
    }

    private fun parseEvents(data: JSONObject, subevents: Boolean = false, depth: Int=1): List<RemoteEvent> {
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
                    live=if (subevents && !require_live) {
                        val eventSlug = json.getString("event")
                        var event = eventMap[eventSlug]
                        if (event == null) {
                            api.eventSlug = eventSlug
                            try {
                                event = api.fetchResource(api.organizerResourceUrl("events/" + eventSlug)!!)

                                if (event.response.code != 200) {
                                    throw IOException()
                                }
                                eventMap[eventSlug] = event
                            } finally {
                                api.eventSlug = conf.eventSlug
                            }
                        }

                        event!!.data!!.getBoolean("live") && json.getBoolean("active")
                    } else if (require_live) { true } else {
                        json.getBoolean("live")
                    }))
        }


        if (!data.isNull("next") && depth < 5) {
            val next = data.getString("next")
            val resp = api.fetchResource(next)
            if (resp.response.code != 200) {
                throw IOException()
            }
            return events + parseEvents(resp.data!!, subevents, depth + 1)
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
        val live: Boolean
) {
    val name: String
        get() = I18nString.toString(name_i18n)
}
