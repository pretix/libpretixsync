package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Quota
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONObject

class QuotaSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
    private val subeventId: Long?,
) : BaseDownloadSyncAdapter<Quota, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = eventSlug,
    fileStorage = fileStorage,
    feedback = feedback,
) {
    override fun getResourceName(): String = "quotas"

    override fun getUrl(): String {
        var url = api.eventResourceUrl(eventSlug, getResourceName())
        url += "?with_availability=true"
        if (this.subeventId != null && this.subeventId > 0L) {
            url += "&subevent=" + this.subeventId
        }
        return url
    }

    override fun getId(obj: Quota): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: Quota): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()

        if (subeventId != null && subeventId > 0L) {
            db.quotaQueries.selectServerIdsByEventSlugAndSubEvent(
                event_slug = eventSlug,
                subevent_id = subeventId,
            ).execute { cursor ->
                while (cursor.next().value) {
                    val id = cursor.getLong(0)
                        ?: throw RuntimeException("server_id column not available")

                    res.add(id)
                }
                QueryResult.Unit
            }
        } else {
            db.quotaQueries.selectServerIdsByEventSlug(eventSlug).execute { cursor ->
                while (cursor.next().value) {
                    val id = cursor.getLong(0)
                        ?: throw RuntimeException("server_id column not available")

                    res.add(id)
                }
                QueryResult.Unit
            }
        }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        val (available, availableNumber) = if (jsonobj.has("available")) {
            val available = if (jsonobj.getBoolean("available")) true else false
            val number =
                if (jsonobj.isNull("available_number")) null else jsonobj.getLong("available_number")

            Pair(available, number)
        } else {
            Pair(null, null)
        }

        val quotaId = db.quotaQueries.transactionWithResult {
            db.quotaQueries.insert(
                available = available,
                available_number = availableNumber,
                event_slug = eventSlug,
                json_data = jsonobj.toString(),
                server_id = jsonobj.getLong("id"),
                size = if (jsonobj.isNull("size")) null else jsonobj.getLong("size"),
                subevent_id = jsonobj.optLong("subevent"),
            )

            db.compatQueries.getLastInsertedQuotaId().executeAsOne()
        }

        upsertItemRelations(quotaId, emptySet(), jsonobj)
    }

    override fun update(obj: Quota, jsonobj: JSONObject) {
        val existingRelations = db.quotaQueries.selectRelationsForQuota(obj.id)
            .executeAsList()
            .map {
                // Not-null assertion needed for SQLite
                it.ItemId!!
            }
            .toSet()

        val (available, availableNumber) = if (jsonobj.has("available")) {
            val available = if (jsonobj.getBoolean("available")) true else false
            val number =
                if (jsonobj.isNull("available_number")) null else jsonobj.getLong("available_number")

            Pair(available, number)
        } else {
            Pair(null, null)
        }

        db.quotaQueries.updateFromJson(
            available = available,
            available_number = availableNumber,
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            size = if (jsonobj.isNull("size")) null else jsonobj.getLong("size"),
            subevent_id = jsonobj.optLong("subevent"),
            id = obj.id,
        )

        upsertItemRelations(obj.id, existingRelations, jsonobj)
    }

    private fun upsertItemRelations(quotaId: Long, existingIds: Set<Long>, jsonobj: JSONObject) {
        val itemsarr = jsonobj.getJSONArray("items")
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
            db.quotaQueries.insertItemRelation(
                item_id = newId,
                quota_id = quotaId,
            )
        }
        for (oldId in existingIds - newIds) {
            db.quotaQueries.deleteItemRelation(
                item_id = oldId,
                quota_id = quotaId,
            )
        }
    }

    override fun delete(key: Long) {
        val quota = db.quotaQueries.selectByServerId(key).executeAsOne()
        db.quotaQueries.deleteItemRelationsForQuota(quota.id)
        db.quotaQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) =
        db.quotaQueries.transaction(false, body)

    override fun runBatch(parameterBatch: List<Long>): List<Quota> =
        db.quotaQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

}
