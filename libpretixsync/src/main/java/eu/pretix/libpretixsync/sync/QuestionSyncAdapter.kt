package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.libpretixsync.sqldelight.Question
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

class QuestionSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseConditionalSyncAdapter<Question, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getResourceName(): String = "questions"

    override fun getId(obj: Question): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: Question): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long>? {
        val res = mutableSetOf<Long>()
        db.questionQueries.selectServerIdsByEventSlug(event_slug = eventSlug).execute { cursor ->
            while (cursor.next().value) {
                val id = cursor.getLong(0) ?: throw RuntimeException("id column not available")
                res.add(id)
            }

            QueryResult.Unit
        }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        val questionId = db.questionQueries.transactionWithResult {
            db.questionQueries.insert(
                event_slug = eventSlug,
                json_data = jsonobj.toString(),
                position = jsonobj.getLong("position"),
                required = jsonobj.optBoolean("required", false),
                server_id = jsonobj.getLong("id"),
            )
            db.compatQueries.getLastInsertedQuestionId().executeAsOne()
        }

        upsertItemRelations(questionId, emptySet(), jsonobj)
    }

    override fun update(obj: Question, jsonobj: JSONObject) {
        val existingRelations = db.questionQueries.selectRelationsForQuestion(obj.id)
            .executeAsList()
            .map {
                // Not-null assertion needed for SQLite
                it.ItemId!!
            }
            .toSet()

        db.questionQueries.updateFromJson(
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            position = jsonobj.getLong("position"),
            required = jsonobj.optBoolean("required", false),
            id = obj.id,
        )

        upsertItemRelations(obj.id, existingRelations, jsonobj)
    }

    private fun upsertItemRelations(questionId: Long, existingIds: Set<Long>, jsonobj: JSONObject) {
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
            db.questionQueries.insertItemRelation(
                item_id = newId,
                question_id = questionId,
            )
        }
        for (oldId in existingIds - newIds) {
            db.questionQueries.deleteItemRelation(
                item_id = oldId,
                question_id = questionId,
            )
        }
    }

    override fun delete(key: Long) {
        val question = db.questionQueries.selectByServerId(key).executeAsOne()
        db.questionQueries.deleteItemRelationsForQuestion(question.id)
        db.questionQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.questionQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<Question> =
        db.questionQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

    @Throws(JSONException::class)
    fun standaloneRefreshFromJSON(data: JSONObject) {
        val known = db.questionQueries.selectByServerId(data.getLong("id")).executeAsOneOrNull()

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
