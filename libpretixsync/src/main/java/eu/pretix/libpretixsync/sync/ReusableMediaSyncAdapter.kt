package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.sqldelight.ResourceSyncStatus
import eu.pretix.libpretixsync.sqldelight.ReusableMedium
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.concurrent.ExecutionException

class ReusableMediaSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseDownloadSyncAdapter<ReusableMedium, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = "__all__",
    fileStorage = fileStorage,
    feedback = feedback,
) {

    private var firstResponseTimestamp: String? = null
    private var lastMediumTimestamp: String? = null
    private var rlm: ResourceSyncStatus? = null

    override fun getResourceName(): String = "reusablemedia"

    private fun rlmName(): String = "reusablemedia"

    override fun getUrl(): String = api.organizerResourceUrl(getResourceName())

    override fun getId(obj: ReusableMedium): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: ReusableMedium): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.reusableMediumQueries.selectServerIds().execute { cursor ->
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
        db.reusableMediumQueries.insert(
            active = jsonobj.getBoolean("active"),
            customer_id = jsonobj.optLong("customer"),
            expires = jsonobj.optString("expires"),
            identifier = jsonobj.getString("identifier"),
            json_data = jsonobj.toString(),
            linked_giftcard_id = jsonobj.optLong("linked_giftcard"),
            linked_orderposition_id = jsonobj.optLong("linked_orderposition"),
            server_id = jsonobj.getLong("id"),
            type = jsonobj.getString("type"),
        )
    }

    override fun update(obj: ReusableMedium, jsonobj: JSONObject) {
        db.reusableMediumQueries.updateFromJson(
            active = jsonobj.getBoolean("active"),
            customer_id = jsonobj.optLong("customer"),
            expires = jsonobj.optString("expires"),
            identifier = jsonobj.getString("identifier"),
            json_data = jsonobj.toString(),
            linked_giftcard_id = jsonobj.optLong("linked_giftcard"),
            linked_orderposition_id = jsonobj.optLong("linked_orderposition"),
            type = jsonobj.getString("type"),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.reusableMediumQueries.deleteByServerId(key)
    }

    override fun deleteUnseen(): Boolean {
        return false
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.reusableMediumQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<ReusableMedium> =
        db.reusableMediumQueries.selectByServerIdList(parameterBatch).executeAsList()

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
                resource = rlmName(),
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
                    val status = if (completed) {
                        "complete"
                    } else {
                        "incomplete:$lastMediumTimestamp"
                    }

                    db.resourceSyncStatusQueries.insert(
                        event_slug = "__all__",
                        last_modified = firstResponseTimestamp,
                        meta = null,
                        resource = rlmName(),
                        status = status,
                    )

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
                    id = resourceSyncStatus.id,
                )
            } else if (!completed && lastMediumTimestamp != null && resourceSyncStatus != null) {
                db.resourceSyncStatusQueries.updateStatus(
                    status = "incomplete:$lastMediumTimestamp",
                    id = resourceSyncStatus.id,
                )
            }
            lastMediumTimestamp = null
            firstResponseTimestamp = null
        }
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    override fun downloadPage(url: String, isFirstPage: Boolean): JSONObject? {
        if (isFirstPage) {
            rlm = db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
                resource = rlmName(),
                event_slug = "__all__",
            ).executeAsOneOrNull()
        }
        var is_continued_fetch = false

        var resUrl = url
        rlm?.let {
            // This resource has been fetched before.
            if (it.status != null && it.status.startsWith("incomplete:")) {
                // Continuing an interrupted fetch

                // Ordering is crucial here: Only because the server returns the orders in the
                // order of creation we can be sure that we don't miss orders created in between our
                // paginated requests.

                is_continued_fetch = true
                try {
                    if (!resUrl.contains("created_since")) {
                        resUrl += "?ordering=datetime&created_since=" + URLEncoder.encode(
                            it.status.substring(11), "UTF-8"
                        )
                    }
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            } else {
                // Diff to last time

                // Ordering is crucial here: Only because the server returns the media in the
                // order of modification we can be sure that we don't miss media created in between our
                // paginated requests. If a medium were to be modified between our fetch of page 1
                // and 2 that originally wasn't part of the result set, we won't see it (as it will
                // be inserted on page 1), but we'll see it the next time, and we will se some
                // duplicates on page 2, but we don't care. The important part is that nothing gets
                // lost "between the pages". If a medium of page 2 gets modified and moves to page
                // one while we fetch page 2, again, we won't see it and we'll see some duplicates,
                // but the next sync will fix it since we always fetch our diff compared to the time
                // of the first page.

                try {
                    if (!resUrl.contains("updated_since")) {
                        resUrl += "?ordering=-updated&updated_since=" + URLEncoder.encode(
                            it.last_modified,
                            "UTF-8"
                        )
                    }
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            }
        }

        val apiResponse = api.fetchResource(resUrl)
        if (isFirstPage && !is_continued_fetch) {
            firstResponseTimestamp = apiResponse.response.header("X-Page-Generated")
        }
        val d = apiResponse.data
        if (apiResponse.response.code == 200) {
            try {
                val res = d!!.getJSONArray("results")
                if (res.length() > 0) {
                    lastMediumTimestamp = res.getJSONObject(res.length() - 1).getString("created")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return d
    }

}
