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

class EventManager(private val store: BlockingEntityStore<Persistable>, private val api: PretixApi, private val conf: ConfigStore) {
    fun getAvailableEvents() : List<RemoteEvent> {
        val eightHoursAgo = URLEncoder.encode((DateTime.now() - Hours.hours(8)).toString())

        val resp_events = api.fetchResource(api.organizerResourceUrl("events") +
                "?live=true&ends_after=$eightHoursAgo")
        if (resp_events.response.code() != 200) {
            throw IOException()
        }
        var events = parseEvents(resp_events.data)

        val resp_subevents = api.fetchResource(api.organizerResourceUrl("subevents")
                + "?active=true&event__live=true&ends_after=$eightHoursAgo")
        if (resp_subevents.response.code() != 200) {
            throw IOException()
        }
        events += parseEvents(resp_subevents.data, subevents=true)

        return events.sortedBy {
            return@sortedBy it.date_from
        }
    }

    private fun parseEvents(data: JSONObject, subevents: Boolean = false): List<RemoteEvent> {
        val events = ArrayList<RemoteEvent>()
        val results = data.getJSONArray("results")
        for (i in 0 until results.length()) {
            val json = results.getJSONObject(i)
            events.add(RemoteEvent(
                    json.getJSONObject("name"),
                    if (subevents) json.getString("event") else json.getString("slug"),
                    DateTime(json.getString("date_from")),
                    if (json.isNull("date_to")) null else {
                        DateTime(json.optString("date_to"))
                    },
                    if (subevents) {
                        json.getLong("id")
                    } else null))
        }


        if (!data.isNull("next")) {
            val next = data.getString("next")
            val resp = api.fetchResource(next)
            if (resp.response.code() != 200) {
                throw IOException()
            }
            return events + parseEvents(resp.data, true)
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
        val subevent_id: Long?
) {
    val name: String
        get() = I18nString.toString(name_i18n)
}
