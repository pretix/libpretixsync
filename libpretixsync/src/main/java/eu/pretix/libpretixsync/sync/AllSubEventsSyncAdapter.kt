package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.sqldelight.ResourceSyncStatus
import eu.pretix.libpretixsync.sqldelight.SubEvent
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.concurrent.ExecutionException

class AllSubEventsSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseDownloadSyncAdapter<SubEvent, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = "__all__",
    fileStorage = fileStorage,
    feedback = feedback,
) {
    private var firstResponseTimestamp: String? = null
    private var rlm: ResourceSyncStatus? = null

    override fun getResourceName(): String = "subevents"

    override fun getUrl(): String {
        return api.organizerResourceUrl(getResourceName())
    }

    override fun getId(obj: SubEvent): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: SubEvent): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.subEventQueries.selectServerIds().execute { cursor ->
            while (cursor.next().value) {
                val id = cursor.getLong(0)
                    ?: throw RuntimeException("server_id column not available")

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
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("date_to")).toDate()
        } else {
            null
        }

        db.subEventQueries.insert(
            active = jsonobj.getBoolean("active"),
            date_from = dateFrom,
            date_to = dateTo,
            event_slug = jsonobj.getString("event"),
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
            event_slug = jsonobj.getString("event"),
            json_data = jsonobj.toString(),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.subEventQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.subEventQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<SubEvent> =
        db.subEventQueries.selectByServerIdList(parameterBatch).executeAsList()

    @Throws(
        JSONException::class,
        ApiException::class,
        ExecutionException::class,
        InterruptedException::class
    )
    override fun download() {
        var completed = false
        try {
            super.download()
            completed = true
        } finally {
            val resourceSyncStatus = db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
                resource = "subevents",
                event_slug = "__all__",
            ).executeAsOneOrNull()

            // We need to cache the response timestamp of the *first* page in the result set to make
            // sure we don't miss anything between this and the next run.
            //
            // If the download failed, completed will be false. In case this was a full fetch
            // (i.e. no timestamp was stored beforehand) we will still store the timestamp to be
            // able to continue properly.
            if (firstResponseTimestamp != null) {
                if (resourceSyncStatus == null) {
                    if (completed) {
                        db.resourceSyncStatusQueries.insert(
                            event_slug = "__all__",
                            meta = null,
                            resource = "subevents",
                            last_modified = firstResponseTimestamp,
                            status = "complete",
                        )
                    }
                } else {
                    if (completed) {
                        db.resourceSyncStatusQueries.updateLastModified(
                            last_modified = firstResponseTimestamp,
                            id = resourceSyncStatus.id,
                        )
                    }
                }
            } else if (completed && resourceSyncStatus != null) {
                db.resourceSyncStatusQueries.updateStatus(
                    status = "complete",
                    id = resourceSyncStatus.id
                )
            }
            firstResponseTimestamp = null
        }
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    override fun downloadPage(url: String, isFirstPage: Boolean): JSONObject? {
        if (isFirstPage) {
            rlm =
                db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
                    resource = "subevents",
                    event_slug = "__all__",
                ).executeAsOneOrNull()
        }

        var resUrl = url
        rlm?.let {
            // This resource has been fetched before.
            // Diff to last time

            // Ordering is crucial here: Only because the server returns the objects in the
            // order of modification we can be sure that we don't miss orders created in between our
            // paginated requests. If an object were to be modified between our fetch of page 1
            // and 2 that originally wasn't part of the result set, we won't see it (as it will
            // be inserted on page 1), but we'll see it the next time, and we will see some
            // duplicates on page 2, but we don't care. The important part is that nothing gets
            // lost "between the pages". If an order of page 2 gets modified and moves to page
            // one while we fetch page 2, again, we won't see it and we'll see some duplicates,
            // but the next sync will fix it since we always fetch our diff compared to the time
            // of the first page.

            try {
                if (!resUrl.contains("modified_since")) {
                    resUrl += if (resUrl.contains("?")) {
                        "&"
                    } else {
                        "?"
                    }
                    resUrl += "ordering=-last_modified&modified_since=" + URLEncoder.encode(
                        it.last_modified,
                        "UTF-8"
                    )
                }
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }

        val apiResponse = api.fetchResource(resUrl)
        if (isFirstPage) {
            firstResponseTimestamp = apiResponse.response.header("X-Page-Generated")
        }
        return apiResponse.data
    }

    override fun deleteUnseen(): Boolean {
        return rlm == null
    }
}
