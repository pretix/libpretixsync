package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.libpretixsync.sqldelight.CheckInList
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

class CheckInListSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
    private val subeventId: Long?,
) : BaseConditionalSyncAdapter<CheckInList, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {
    override fun getResourceName(): String = "checkinlists"

    override fun getUrl(): String {
        var url = api.eventResourceUrl(eventSlug, getResourceName())
        url += "?exclude=checkin_count&exclude=position_count"
        if (this.subeventId != null && this.subeventId > 0L) {
            url += "&subevent_match=" + this.subeventId
        }
        return url
    }

    public override fun getMeta(): String {
        return if (this.subeventId != null && this.subeventId > 0L) {
            "subevent=" + this.subeventId
        } else {
            super.getMeta()
        }
    }

    override fun getId(obj: CheckInList): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: CheckInList): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.checkInListQueries.selectServerIdsByEventSlug(eventSlug).execute { cursor ->
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
        val listId = db.checkInListQueries.transactionWithResult {
            db.checkInListQueries.insert(
                all_items = jsonobj.optBoolean("all_products"),
                event_slug = eventSlug,
                include_pending = jsonobj.optBoolean("include_pending"),
                json_data = jsonobj.toString(),
                name = jsonobj.optString("name", ""),
                server_id = jsonobj.getLong("id"),
                subevent_id = jsonobj.optLong("subevent"),
            )

            db.compatQueries.getLastInsertedCheckInListId().executeAsOne()
        }

        upsertItemRelations(listId, emptySet(), jsonobj)
    }

    override fun update(obj: CheckInList, jsonobj: JSONObject) {
        val existingRelations = db.checkInListQueries.selectRelationsForList(obj.id)
            .executeAsList()
            .map {
                // Not-null assertion needed for SQLite
                it.ItemId!!
            }
            .toSet()

        db.checkInListQueries.updateFromJson(
            all_items = jsonobj.optBoolean("all_products"),
            event_slug = eventSlug,
            include_pending = jsonobj.optBoolean("include_pending"),
            json_data = jsonobj.toString(),
            name = jsonobj.optString("name", ""),
            subevent_id = jsonobj.optLong("subevent"),
            id = obj.id,
        )

        upsertItemRelations(obj.id, existingRelations, jsonobj)
    }

    private fun upsertItemRelations(listId: Long, existingIds: Set<Long>, jsonobj: JSONObject) {
        val itemsarr = jsonobj.getJSONArray("limit_products")
        val itemids = ArrayList<Long>(itemsarr.length())
        for (i in 0 until itemsarr.length()) {
            itemids.add(itemsarr.getLong(i))
        }
        val newIds = if (itemids.isNotEmpty()) {
            db.itemQueries.selectByServerIdListAndEventSlug(
                server_id = itemids,
                event_slug = eventSlug,
            ).executeAsList().map { it.id }.toSet()
        } else {
            emptySet()
        }

        for (newId in newIds - existingIds) {
            db.checkInListQueries.insertItemRelation(
                item_id = newId,
                checkin_list_id = listId,
            )
        }
        for (oldId in existingIds - newIds) {
            db.checkInListQueries.deleteItemRelation(
                item_id = oldId,
                checkin_list_id = listId,
            )
        }
    }

    override fun delete(key: Long) {
        val list = db.checkInListQueries.selectByServerId(key).executeAsOne()
        db.checkInListQueries.deleteItemRelationsForList(list.id)
        db.checkInListQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.checkInListQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<CheckInList> =
        db.checkInListQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

    @Throws(JSONException::class)
    fun standaloneRefreshFromJSON(data: JSONObject) {
        val known = db.checkInListQueries.selectByServerId(data.getLong("id")).executeAsOneOrNull()

        // Store object
        data.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION)
        data.put("__libpretixsync_syncCycleId", syncCycleId)
        if (known == null) {
            insert(data)
        } else {
            val old = JSONObject(known.json_data!!)
            if (!JSONUtils.similar(data, old)) {
                update(known, data)
            }
        }
    }

}
