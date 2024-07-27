package eu.pretix.libpretixsync.sync

import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONException
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.ExecutionException

abstract class BaseConditionalSyncAdapter<T, K>(
    db: SyncDatabase,
    api: PretixApi,
    syncCycleId: String,
    eventSlug: String,
    fileStorage: FileStorage,
    feedback: ProgressFeedback?,
) : BaseDownloadSyncAdapter<T, K>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = eventSlug,
    fileStorage = fileStorage,
    feedback = feedback,
) {
    private var firstResponse: PretixApi.ApiResponse? = null

    @Throws(ApiException::class, ResourceNotModified::class)
    override fun downloadPage(url: String, isFirstPage: Boolean): JSONObject? {
        val resourceSyncStatus = db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
            resource = getResourceName(),
            event_slug = eventSlug,
        ).executeAsOneOrNull()

        val lastModified = if (resourceSyncStatus == null) {
            null
        } else {
            if (getMeta() != resourceSyncStatus.meta && !(getMeta() == "" && resourceSyncStatus.meta == null)) {
                db.resourceSyncStatusQueries.deleteById(resourceSyncStatus.id)
                Date().toString()
            } else {
                resourceSyncStatus.last_modified
            }
        }
        val apiResponse = api.fetchResource(url, lastModified)

        if (isFirstPage) {
            firstResponse = apiResponse
        }
        return apiResponse.data
    }

    protected open fun getMeta(): String {
        return ""
    }

    @Throws(
        JSONException::class,
        ApiException::class,
        ExecutionException::class,
        InterruptedException::class
    )
    override fun download() {
        firstResponse = null
        super.download()

        val currentResponse = firstResponse
        if (currentResponse != null) {
            val resourceSyncStatus = db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
                resource = getResourceName(),
                event_slug = eventSlug,
            ).executeAsOneOrNull()

            val lastModified = if (currentResponse.response.header("Last-Modified") != null) {
                currentResponse.response.header("Last-Modified")
            } else {
                null
            }

            if (lastModified != null && resourceSyncStatus == null) {
                db.resourceSyncStatusQueries.insert(
                    event_slug = eventSlug,
                    last_modified = lastModified,
                    resource = getResourceName(),
                    meta = getMeta(),
                    status = null,
                )
            } else if (lastModified != null && resourceSyncStatus != null) {
                db.resourceSyncStatusQueries.updateLastModifiedAndMeta(
                    last_modified = lastModified,
                    meta = getMeta(),
                    id = resourceSyncStatus.id,
                )
            }
            firstResponse = null
        }
    }
}
