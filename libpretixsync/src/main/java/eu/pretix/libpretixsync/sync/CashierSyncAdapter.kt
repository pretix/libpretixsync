package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Cashier
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import org.json.JSONObject

class CashierSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    api: PretixApi,
    syncCycleId: String,
    feedback: SyncManager.ProgressFeedback?,
) : BaseConditionalSyncAdapter<Cashier, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = "__all__",
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getResourceName(): String = "cashiers"

    override fun getUrl(): String = api.organizerResourceUrl("pos/" + getResourceName())

    override fun getId(obj: Cashier): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: Cashier): JSONObject = JSONObject(obj.json_data)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.cashierQueries.selectServerIds().execute { cursor ->
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
        db.cashierQueries.insert(
            active = jsonobj.getBoolean("active"),
            json_data = jsonobj.toString(),
            name = jsonobj.getString("name"),
            pin = if (jsonobj.isNull("pin")) "" else jsonobj.getString("pin"),
            server_id = jsonobj.getLong("id"),
            userid = jsonobj.getString("userid"),
        )
    }

    override fun update(obj: Cashier, jsonobj: JSONObject) {
        db.cashierQueries.updateFromJson(
            active = jsonobj.getBoolean("active"),
            json_data = jsonobj.toString(),
            name = jsonobj.getString("name"),
            pin = if (jsonobj.isNull("pin")) "" else jsonobj.getString("pin"),
            userid = jsonobj.getString("userid"),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.cashierQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.cashierQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<Cashier> =
        db.cashierQueries.selectByServerIdList(parameterBatch).executeAsList()

}
