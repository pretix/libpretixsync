package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.SentryInterface
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Migrations
import eu.pretix.libpretixsync.sqldelight.Questionnaire
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sqldelight.writeTransaction
import eu.pretix.libpretixsync.sqldelight.writeTransactionWithResult
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

class QuestionnaireSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
    private val sentry: SentryInterface? = null,
    private val salesChannel: String = "pretixpos",
) : BaseConditionalSyncAdapter<Questionnaire, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getResourceName(): String = "questionnaires"

    override fun getId(obj: Questionnaire): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: Questionnaire): JSONObject = JSONObject(obj.json_data!!)

    override fun getUrl(): String {
        return api.eventResourceUrl(eventSlug, getResourceName()) + "?expand=rows.datafield&expand_name_fields=true"
    }

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.questionnaireQueries.selectServerIdsByEventSlug(eventSlug).execute { cursor ->
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
        // Called from within transaction in processPage
        // Set noEnclosing=false but keep transaction for when this gets called directly
        val questionnaireId = db.questionnaireQueries.writeTransactionWithResult(db, sentry, false) {
            db.questionnaireQueries.insert(
                event_slug = eventSlug,
                json_data = jsonobj.toString(),
                position = jsonobj.getLong("position"),
                type = jsonobj.getString("type"),
                server_id = jsonobj.getLong("id"),
            )
            db.compatQueries.getLastInsertedQuestionnaireId().executeAsOne()
        }

        upsertItemRelations(questionnaireId, emptySet(), jsonobj)
    }

    override fun update(obj: Questionnaire, jsonobj: JSONObject) {
        val existingRelations = db.questionnaireQueries.selectRelationsForQuestionnaire(obj.id)
            .executeAsList()
            .map {
                // Not-null assertion needed for SQLite
                it.ItemId!!
            }
            .toSet()

        db.questionnaireQueries.updateFromJson(
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            position = jsonobj.getLong("position"),
            type = jsonobj.getString("type"),
            id = obj.id,
        )

        upsertItemRelations(obj.id, existingRelations, jsonobj)
    }

    private fun upsertItemRelations(questionnaireId: Long, existingIds: Set<Long>, jsonobj: JSONObject) {
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
            db.questionnaireQueries.insertItemRelation(
                item_id = newId,
                questionnaire_id = questionnaireId,
            )
        }
        for (oldId in existingIds - newIds) {
            db.questionnaireQueries.deleteItemRelation(
                item_id = oldId,
                questionnaire_id = questionnaireId,
            )
        }
    }

    override fun delete(key: Long) {
        val question = db.questionnaireQueries.selectByServerId(key).executeAsOne()
        db.questionnaireQueries.deleteItemRelationsForQuestionnaire(question.id)
        db.questionnaireQueries.deleteByServerId(key)
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.questionnaireQueries.writeTransaction(db = db, sentry = sentry, body = body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<Questionnaire> =
        db.questionnaireQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

    @Throws(JSONException::class)
    fun standaloneRefreshFromJSON(data: JSONObject) {
        val known = db.questionnaireQueries.selectByServerId(data.getLong("id")).executeAsOneOrNull()

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
