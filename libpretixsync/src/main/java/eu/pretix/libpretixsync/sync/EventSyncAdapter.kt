package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.libpretixsync.sqldelight.Event
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.JSONUtils
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONException
import org.json.JSONObject

class EventSyncAdapter(
    db: SyncDatabase,
    eventSlug: String,
    key: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback? = null,
) : SqBaseSingleObjectSyncAdapter<Event>(
    db = db,
    eventSlug = eventSlug,
    key = key,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getKnownObject(): Event? {
        val known = db.eventQueries.selectBySlug(eventSlug).executeAsList()

        return if (known.isEmpty()) {
            null
        } else if (known.size == 1) {
            known[0]
        } else {
            // What's going on here? Let's delete and re-fetch
            db.eventQueries.deleteBySlug(eventSlug)
            null
        }
    }

    override fun getResourceName(): String = "events"

    override fun getUrl(): String = api.organizerResourceUrl("events/$key")

    override fun getJSON(obj: Event): JSONObject = JSONObject(obj.json_data!!)

    override fun insert(jsonobj: JSONObject) {
        val dateFrom =
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from"))
                .toDate()

        val dateTo = if (!jsonobj.isNull("date_to")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate()
        } else {
            null
        }

        db.eventQueries.insert(
            currency = jsonobj.getString("currency"),
            date_to = dateTo,
            date_from = dateFrom,
            has_subevents = jsonobj.getBoolean("has_subevents"),
            json_data = jsonobj.toString(),
            live = jsonobj.getBoolean("live"),
            slug = jsonobj.getString("slug"),
        )
    }

    override fun update(obj: Event, jsonobj: JSONObject) {
        val dateFrom =
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from"))
                .toDate()

        val dateTo = if (!jsonobj.isNull("date_to")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate()
        } else {
            null
        }

        db.eventQueries.updateFromJson(
            currency = jsonobj.getString("currency"),
            date_to = dateTo,
            date_from = dateFrom,
            has_subevents = jsonobj.getBoolean("has_subevents"),
            json_data = jsonobj.toString(),
            live = jsonobj.getBoolean("live"),
            slug = obj.slug,
        )
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.eventQueries.transaction(false, body)
    }

    @Throws(JSONException::class)
    fun standaloneRefreshFromJSON(data: JSONObject) {
        // Store object
        data.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION)
        data.put("__libpretixsync_syncCycleId", syncCycleId)
        val known = getKnownObject()
        if (known == null) {
            insert(data)
        } else {
            val old = JSONObject(known.json_data!!)
            if (!JSONUtils.similar(data, old)) {
                update(known, data)
            }
        }
    }
}
