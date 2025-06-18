package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.sqldelight.Migrations
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.CanceledState
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

abstract class BaseSingleObjectSyncAdapter<T>(
    protected var db: SyncDatabase,
    protected var eventSlug: String,
    protected var key: String,
    protected var api: PretixApi,
    protected var syncCycleId: String,
    protected var feedback: ProgressFeedback? = null,
) : DownloadSyncAdapter {

    private var canceledState: CanceledState? = null

    override fun download() {
        if (feedback != null) {
            feedback!!.postFeedback("Downloading " + getResourceName() + " [" + eventSlug + "] …")
        }
        try {
            val data = downloadRawData()
            processData(data)
        } catch (e: ResourceNotModified) {
            // Do nothing
        }
    }

    override fun setCancelState(state: SyncManager.CanceledState?) {
        canceledState = state
    }

    abstract fun getKnownObject(): T?

    protected fun processData(data: JSONObject) {
        try {
            data.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION)
            data.put("__libpretixsync_syncCycleId", syncCycleId)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        runInTransaction {
            val known = getKnownObject()
            if (known != null) {
                val old = getJSON(known)
                if (!JSONUtils.similar(data, old)) {
                    update(known, data)
                }
            } else {
                insert(data)
            }
        }
    }

    protected open fun getUrl(): String {
        return api.eventResourceUrl(eventSlug, getResourceName() + "/" + key)
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    protected fun downloadRawData(): JSONObject {
        return downloadPage(getUrl())
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    protected fun downloadPage(url: String): JSONObject {
        return api.fetchResource(url).data!!
    }

    abstract fun getResourceName(): String

    abstract fun runInTransaction(body: TransactionWithoutReturn.() -> Unit)

    abstract fun insert(jsonobj: JSONObject)

    abstract fun update(obj: T, jsonobj: JSONObject)

    abstract fun getJSON(obj: T): JSONObject
}
