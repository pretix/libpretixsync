package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Discount
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONObject

class DiscountSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseConditionalSyncAdapter<Discount, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getResourceName(): String = "discounts"

    override fun getId(obj: Discount): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: Discount): JSONObject = JSONObject(obj.json_data)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.discountQueries.selectServerIdsByEventSlug(eventSlug)
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
        val availableFrom = if (!jsonobj.isNull("available_from")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("available_from")).toDate()
        } else {
            null
        }
        val availableUntil = if (!jsonobj.isNull("available_until")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("available_until")).toDate()
        } else {
            null
        }
        db.discountQueries.insert(
            server_id = jsonobj.getLong("id"),
            event_slug = eventSlug,
            active = jsonobj.getBoolean("active"),
            available_from = availableFrom,
            available_until = availableUntil,
            position = jsonobj.getLong("position"),
            json_data = jsonobj.toString(),
        )
    }

    override fun update(obj: Discount, jsonobj: JSONObject) {
        val availableFrom = if (!jsonobj.isNull("available_from")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("available_from")).toDate()
        } else {
            null
        }
        val availableUntil = if (!jsonobj.isNull("available_until")) {
            ISODateTimeFormat.dateTimeParser().parseDateTime(jsonobj.getString("available_until")).toDate()
        } else {
            null
        }
        db.discountQueries.updateFromJson(
            event_slug = eventSlug,
            active = jsonobj.getBoolean("active"),
            available_from = availableFrom,
            available_until = availableUntil,
            position = jsonobj.getLong("position"),
            json_data = jsonobj.toString(),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.discountQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.discountQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<Discount> =
        db.discountQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()
}
