package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.libpretixsync.sqldelight.SubEvent
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.JSONUtils
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONException
import org.json.JSONObject

class SubEventSyncAdapter(
    db: SyncDatabase,
    eventSlug: String,
    key: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback? = null,
) : BaseSingleObjectSyncAdapter<SubEvent>(
    db = db,
    eventSlug = eventSlug,
    key = key,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getKnownObject(): SubEvent? {
        val known = db.subEventQueries.selectByServerId(key.toLong()).executeAsList()

        return if (known.isEmpty()) {
            null
        } else if (known.size == 1) {
            known[0]
        } else {
            // What's going on here? Let's delete and re-fetch
            db.subEventQueries.deleteByServerId(key.toLong())
            null
        }
    }

    override fun insert(jsonobj: JSONObject) {
        val dateFrom =
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from"))
                .toDate()

        val dateTo = if (!jsonobj.isNull("date_to")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate()
        } else {
            null
        }

        db.subEventQueries.insert(
            active = jsonobj.getBoolean("active"),
            date_from = dateFrom,
            date_to = dateTo,
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            server_id = jsonobj.getLong("id"),
        )
    }

    override fun update(obj: SubEvent, jsonobj: JSONObject) {
        val dateFrom =
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from"))
                .toDate()

        val dateTo = if (!jsonobj.isNull("date_to")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate()
        } else {
            null
        }

        db.subEventQueries.updateFromJson(
            active = jsonobj.getBoolean("active"),
            date_from = dateFrom,
            date_to = dateTo,
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            id = obj.id,
        )
    }

    override fun getResourceName(): String = "subevents"

    override fun getJSON(obj: SubEvent): JSONObject = JSONObject(obj.json_data!!)

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.subEventQueries.transaction(false, body)
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
