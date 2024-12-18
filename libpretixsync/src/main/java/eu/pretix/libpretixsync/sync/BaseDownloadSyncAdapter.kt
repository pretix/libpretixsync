package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.CanceledState
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.JSONUtils
import java8.util.concurrent.CompletableFuture
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

abstract class BaseDownloadSyncAdapter<T, K>(
    protected var db: SyncDatabase,
    protected var api: PretixApi,
    protected var syncCycleId: String,
    protected var eventSlug: String,
    protected var fileStorage: FileStorage,
    protected var feedback: ProgressFeedback?,
) : DownloadSyncAdapter, BatchedQueryIterator.BatchedQueryCall<K, T> {
    protected var knownIDs: MutableSet<K>? = null
    protected val seenIDs: MutableSet<K> = mutableSetOf()
    protected var sizeBefore = 0L
    protected var threadPool = Executors.newCachedThreadPool()
    protected var total = 0
    protected var inserted = 0
    protected var totalOnline = 0
    protected var canceledState: CanceledState? = null

    override fun setCancelState(state: CanceledState?) {
        canceledState = state
    }

    @Throws(
        JSONException::class,
        ApiException::class,
        ExecutionException::class,
        InterruptedException::class
    )
    override fun download() {
        feedback?.postFeedback("Downloading " + getResourceName() + " [" + eventSlug + "]  …")

        try {
            total = 0
            inserted = 0
            knownIDs = queryKnownIDs()
            sizeBefore = getKnownCount()
            seenIDs.clear()

            downloadData()

            if (deleteUnseen()) {
                val ids = knownIDs
                    ?: throw RuntimeException("knownIDs can't be null if deleteUnseen() returns true.")

                for ((key, value) in queryKnownObjects(ids)) {
                    prepareDelete(value)
                    delete(key)
                }
            }
        } catch (e: ResourceNotModified) {
            // Do nothing
        }
    }

    protected open fun getKnownCount(): Long = knownIDs.let {
        if (it == null) {
            throw RuntimeException("knownIDs can't be null if deleteUnseen() returns true.")
        }
        return it.size.toLong()
    }

    protected open fun queryKnownObjects(ids: Set<K>): MutableMap<K, T> {
        if (ids.isEmpty()) {
            return mutableMapOf()
        }

        val it = BatchedQueryIterator(ids.iterator(), this)
        val known = mutableMapOf<K, T>()
        while (it.hasNext()) {
            try {
                val obj = it.next()
                val key = getId(obj)
                if (known.containsKey(key)) {
                    delete(key)
                }
                known[key] = obj
            } catch (e: BatchEmptyException) {
                // Ignore
            }
        }
        return known
    }

    protected open fun preprocessObject(obj: JSONObject): JSONObject {
        return obj
    }

    protected fun processPage(data: JSONArray) {
        val l = data.length()
        runInTransaction {
            val fetchIds: MutableSet<K> = HashSet()
            for (i in 0 until l) {
                val jsonobj = data.getJSONObject(i)
                fetchIds.add(getId(jsonobj))
            }
            val known: MutableMap<K, T> = queryKnownObjects(fetchIds)
            for (i in 0 until l) {
                val jsonobj = preprocessObject(data.getJSONObject(i))
                jsonobj.put(
                    "__libpretixsync_dbversion",
                    Migrations.CURRENT_VERSION
                )
                jsonobj.put("__libpretixsync_syncCycleId", syncCycleId)
                val jsonid: K = getId(jsonobj)
                var obj: T?
                var old: JSONObject? = null

                if (seenIDs.contains(jsonid)) {
                    continue
                } else if (known.containsKey(jsonid)) {
                    obj = known.getValue(jsonid)
                    old = getJSON(obj)
                } else {
                    obj = null
                }
                if (obj != null) {
                    known.remove(jsonid)
                    knownIDs?.let {
                        it.remove(jsonid)
                    }
                    if (!JSONUtils.similar(jsonobj, old)) {
                        update(obj, jsonobj)
                    }
                } else {
                    insert(jsonobj)
                    inserted += 1
                }
                seenIDs.add(jsonid)
            }

            afterPage()
            null
        }
        total += l

        feedback?.postFeedback("Processed " + total + "/" + totalOnline + " " + getResourceName() + " (total in database: ~" + (sizeBefore + inserted) + ") " + " [" + eventSlug + "] …")
    }

    protected open fun afterPage() {}

    protected open fun prepareDelete(obj: T) {}

    protected open fun deleteUnseen(): Boolean {
        return true
    }

    protected fun asyncProcessPage(data: JSONArray): CompletableFuture<Boolean> {
        val completableFuture = CompletableFuture<Boolean>()
        threadPool.submit {
            try {
                processPage(data)
            } catch (e: Exception) {
                completableFuture.completeExceptionally(e)
            } finally {
                completableFuture.complete(true)
            }
        }
        return completableFuture
    }

    protected open fun getUrl(): String {
        return api.eventResourceUrl(eventSlug, getResourceName())
    }

    @Throws(
        JSONException::class,
        ApiException::class,
        ResourceNotModified::class,
        ExecutionException::class,
        InterruptedException::class
    )
    protected open fun downloadData() {
        var url = getUrl()
        var isFirstPage = true
        var future: CompletableFuture<Boolean>? = null
        try {
            while (true) {
                val isCanceled = canceledState.let {
                    it != null && it.isCanceled
                }
                if (isCanceled) {
                    throw InterruptedException()
                }

                val page = downloadPage(url, isFirstPage) ?: throw ApiException("page is null")

                future?.get()
                totalOnline = page.getInt("count")
                future = asyncProcessPage(page.getJSONArray("results"))
                if (page.isNull("next")) {
                    break
                }
                url = page.getString("next")
                isFirstPage = false
            }
        } finally {
            future?.get()
        }
    }

    @Throws(ApiException::class, ResourceNotModified::class)
    protected open fun downloadPage(url: String, isFirstPage: Boolean): JSONObject? {
        return api.fetchResource(url).data
    }

    abstract fun getResourceName(): String

    abstract fun getId(obj: T): K

    @Throws(JSONException::class)
    abstract fun getId(obj: JSONObject): K

    @Throws(JSONException::class)
    abstract fun getJSON(obj: T): JSONObject

    abstract fun queryKnownIDs(): MutableSet<K>?

    abstract fun insert(jsonobj: JSONObject)

    abstract fun update(obj: T, jsonobj: JSONObject)

    abstract fun delete(key: K)

    abstract fun runInTransaction(body: TransactionWithoutReturn.() -> Unit)
}
