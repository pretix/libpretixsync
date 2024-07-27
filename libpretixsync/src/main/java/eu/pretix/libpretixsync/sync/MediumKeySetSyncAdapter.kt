package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.api.ResourceNotModified
import eu.pretix.libpretixsync.sqldelight.MediumKeySet
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutionException

class MediumKeySetSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
    private var data: JSONArray,
) : BaseDownloadSyncAdapter<MediumKeySet, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = "__all__",
    fileStorage = fileStorage,
    feedback = feedback,
) {

    override fun getResourceName(): String = "mediumkeyset"

    // TODO: Seems unused?
    private fun rlmName(): String = "mediumkeyset"

    override fun getId(obj: MediumKeySet): Long = obj.public_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("public_id")

    override fun getJSON(obj: MediumKeySet): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long>? {
        val res = mutableSetOf<Long>()
        db.mediumKeySetQueries.selectPublicIds().execute { cursor ->
            while (cursor.next().value) {
                val id =
                    cursor.getLong(0) ?: throw RuntimeException("public_id column not available")
                res.add(id)
            }

            QueryResult.Unit
        }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        db.mediumKeySetQueries.insert(
            active = jsonobj.getBoolean("active"),
            diversification_key = jsonobj.getString("diversification_key"),
            json_data = jsonobj.toString(),
            media_type = jsonobj.getString("media_type"),
            organizer = jsonobj.getString("organizer"),
            public_id = jsonobj.getLong("public_id"),
            uid_key = jsonobj.getString("uid_key")
        )
    }

    override fun update(obj: MediumKeySet, jsonobj: JSONObject) {
        db.mediumKeySetQueries.updateFromJson(
            active = jsonobj.getBoolean("active"),
            diversification_key = jsonobj.getString("diversification_key"),
            json_data = jsonobj.toString(),
            media_type = jsonobj.getString("media_type"),
            organizer = jsonobj.getString("organizer"),
            public_id = jsonobj.getLong("public_id"),
            uid_key = jsonobj.getString("uid_key"),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.mediumKeySetQueries.deleteByPublicId(key)
    }

    override fun deleteUnseen(): Boolean {
        return true
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.mediumKeySetQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<MediumKeySet> =
        db.mediumKeySetQueries.selectByPublicIdList(parameterBatch).executeAsList()

    @Throws(
        JSONException::class,
        ApiException::class,
        ResourceNotModified::class,
        ExecutionException::class,
        InterruptedException::class
    )
    override fun downloadData() {
        asyncProcessPage(data).get()
    }
}
