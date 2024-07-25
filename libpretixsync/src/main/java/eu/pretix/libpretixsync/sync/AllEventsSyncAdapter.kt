package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Event
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONObject

class AllEventsSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : SqBaseDownloadSyncAdapter<Event, String>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = "__all__",
    fileStorage = fileStorage,
    feedback = feedback,
) {
    override fun getResourceName(): String = "events"

    override fun getUrl(): String {
        return api.organizerResourceUrl(getResourceName())
    }

    override fun getId(obj: Event): String = obj.slug!!

    override fun getId(obj: JSONObject): String = obj.getString("slug")

    override fun getJSON(obj: Event): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<String>? {
        val res = mutableSetOf<String>()
        db.eventQueries.selectSlugs().execute { cursor ->
            while (cursor.next().value) {
                val id = cursor.getString(0) ?: throw RuntimeException("slug column not available")
                res.add(id)
            }

            QueryResult.Unit
        }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        val dateFrom =
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_from"))
                .toDate()

        val dateTo = if (!jsonobj.isNull("date_to")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to"))
                .toDate()
        } else {
            null
        }

        db.eventQueries.insert(
            currency = jsonobj.getString("currency"),
            date_from = dateFrom,
            date_to = dateTo,
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

    override fun delete(key: String) {
        db.eventQueries.deleteBySlug(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.eventQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<String>): List<Event> =
        db.eventQueries.selectBySlugList(parameterBatch).executeAsList()
}
