package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.sqldelight.BlockedTicketSecret
import eu.pretix.libpretixsync.sqldelight.ResourceSyncStatus
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.concurrent.ExecutionException

class BlockedTicketSecretSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : SqBaseDownloadSyncAdapter<BlockedTicketSecret, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = eventSlug,
    fileStorage = fileStorage,
    feedback = feedback,
) {

    private var firstResponseTimestamp: String? = null
    private var rlm: ResourceSyncStatus? = null

    override fun getResourceName(): String = "blockedsecrets"

    override fun getId(obj: BlockedTicketSecret): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: BlockedTicketSecret): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long>? {
        val res = mutableSetOf<Long>()
        db.blockedTicketSecretQueries.selectServerIdsByEventSlug(eventSlug).execute { cursor ->
            while (cursor.next().value) {
                val id =
                    cursor.getLong(0) ?: throw RuntimeException("server_id column not available")
                res.add(id)
            }

            QueryResult.Unit
        }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        val blocked = jsonobj.getBoolean("blocked")
        // If not blocked and not yet in our database, we don't need to save it, as we only care
        // about blocked entries.
        if (!blocked) {
            return
        }

        db.blockedTicketSecretQueries.insert(
            blocked = blocked,
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            secret = jsonobj.getString("secret"),
            server_id = jsonobj.getLong("id"),
            updated = jsonobj.getString("updated"),
        )
    }

    override fun update(obj: BlockedTicketSecret, jsonobj: JSONObject) {
        // TODO: Test new behaviour. Original version had no update case
        db.blockedTicketSecretQueries.updateFromJson(
            blocked = jsonobj.getBoolean("blocked"),
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            secret = jsonobj.getString("secret"),
            updated = jsonobj.getString("updated"),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.blockedTicketSecretQueries.deleteByServerId(key)
    }

    override fun deleteUnseen(): Boolean {
        return rlm == null
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.blockedTicketSecretQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<BlockedTicketSecret> =
        db.blockedTicketSecretQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

    override fun autoPersist(): Boolean {
        return false
    }

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
                resource = getResourceName(),
                event_slug = eventSlug,
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
                            event_slug = eventSlug,
                            last_modified = firstResponseTimestamp,
                            meta = null,
                            resource = getResourceName(),
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

        // We clean up unblocked records after the sync
        db.blockedTicketSecretQueries.deleteNotBlocked()
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    override fun downloadPage(url: String, isFirstPage: Boolean): JSONObject? {
        if (isFirstPage) {
            rlm = db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
                resource = getResourceName(),
                event_slug = eventSlug,
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
                if (!resUrl.contains("updated_since")) {
                    resUrl += if (resUrl.contains("?")) {
                        "&"
                    } else {
                        "?"
                    }
                    resUrl += "ordering=-updated&updated_since=" + URLEncoder.encode(
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
            try {
                val results = apiResponse.data!!.getJSONArray("results")
                if (results.length() > 0) {
                    firstResponseTimestamp = results.getJSONObject(0).getString("updated")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }
        return apiResponse.data
    }

}
