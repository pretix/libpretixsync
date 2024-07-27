package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sqldelight.TaxRule
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONObject

class TaxRuleSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseConditionalSyncAdapter<TaxRule, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getResourceName(): String = "taxrules"

    override fun getId(obj: TaxRule): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: TaxRule): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long>? {
        val res = mutableSetOf<Long>()
        db.taxRuleQueries.selectServerIdsByEventSlug(eventSlug).execute { cursor ->
            while (cursor.next().value) {
                val id = cursor.getLong(0) ?: throw RuntimeException("id column not available")
                res.add(id)
            }
            QueryResult.Unit
        }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        db.taxRuleQueries.insert(
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            server_id = jsonobj.getLong("id"),
        )
    }

    override fun update(obj: TaxRule, jsonobj: JSONObject) {
        db.taxRuleQueries.updateFromJson(
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.taxRuleQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.taxRuleQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<TaxRule> =
        db.taxRuleQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

}
