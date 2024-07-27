package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.ItemCategory
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONObject

class ItemCategorySyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseConditionalSyncAdapter<ItemCategory, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getResourceName(): String = "categories"

    override fun getId(obj: ItemCategory): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: ItemCategory): JSONObject = JSONObject(obj.json_data)

    override fun queryKnownIDs(): MutableSet<Long>? {
        val res = mutableSetOf<Long>()
        db.itemCategoryQueries.selectServerIdsByEventSlug(event_slug = eventSlug)
            .execute { cursor ->
                while (cursor.next().value) {
                    val id = cursor.getLong(0) ?: throw RuntimeException("id column not available")
                    res.add(id)
                }

                QueryResult.Unit
            }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        db.itemCategoryQueries.insert(
            event_slug = eventSlug,
            is_addon = jsonobj.optBoolean("is_addon", false),
            json_data = jsonobj.toString(),
            position = jsonobj.getLong("position"),
            server_id = jsonobj.getLong("id"),
        )
    }

    override fun update(obj: ItemCategory, jsonobj: JSONObject) {
        db.itemCategoryQueries.updateFromJson(
            event_slug = eventSlug,
            is_addon = jsonobj.optBoolean("is_addon", false),
            json_data = jsonobj.toString(),
            position = jsonobj.getLong("position"),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.itemCategoryQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.itemCategoryQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<ItemCategory> =
        db.itemCategoryQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()
}
