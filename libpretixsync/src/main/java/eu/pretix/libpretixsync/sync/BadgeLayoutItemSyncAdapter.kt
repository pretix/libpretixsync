package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.BadgeLayoutItem
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONObject


class BadgeLayoutItemSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseDownloadSyncAdapter<BadgeLayoutItem, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = eventSlug,
    fileStorage = fileStorage,
    feedback = feedback,
) {

    private val itemCache: MutableMap<Long, Long> = HashMap()
    private val layoutCache: MutableMap<Long, Long> = HashMap()

    override fun getResourceName(): String = "badgeitems"

    override fun getId(obj: BadgeLayoutItem): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: BadgeLayoutItem): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.badgeLayoutItemQueries.selectServerIdsByEventSlug(event_slug = eventSlug)
            .execute { cursor ->
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
        val item = getItemId(jsonobj.getLong("item"))

        val layout = if (!jsonobj.isNull("layout")) {
            getLayoutId(jsonobj.getLong("layout"))
        } else {
            null
        }

        db.badgeLayoutItemQueries.insert(
            json_data = jsonobj.toString(),
            server_id = jsonobj.getLong("id"),
            item = item,
            layout = layout,
        )
    }

    override fun update(obj: BadgeLayoutItem, jsonobj: JSONObject) {
        val item = getItemId(jsonobj.getLong("item"))

        val layout = if (!jsonobj.isNull("layout")) {
            getLayoutId(jsonobj.getLong("layout"))
        } else {
            null
        }

        db.badgeLayoutItemQueries.updateFromJson(
            json_data = jsonobj.toString(),
            item = item,
            layout = layout,
            id = obj.id,
        )
    }

    private fun getItemId(id: Long): Long? {
        if (itemCache.isEmpty()) {
            val items = db.itemQueries.selectByEventSlug(eventSlug).executeAsList()
            for (item in items) {
                itemCache[item.server_id] = item.id
            }
        }
        return itemCache[id]
    }

    private fun getLayoutId(id: Long): Long? {
        if (layoutCache.isEmpty()) {
            val layouts = db.badgeLayoutQueries.selectByEventSlug(eventSlug).executeAsList()
            for (layout in layouts) {
                layoutCache[layout.server_id!!] = layout.id
            }
        }
        return layoutCache[id]
    }

    override fun delete(key: Long) {
        db.badgeLayoutItemQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.badgeLayoutItemQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<BadgeLayoutItem> =
        db.badgeLayoutItemQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

}
