package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.BadgeLayout
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.HashUtils
import org.json.JSONObject
import java.io.IOException

class BadgeLayoutSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseDownloadSyncAdapter<BadgeLayout, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = eventSlug,
    fileStorage = fileStorage,
    feedback = feedback,
) {
    override fun getResourceName(): String = "badgelayouts"

    override fun getId(obj: BadgeLayout): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: BadgeLayout): JSONObject = JSONObject(obj.json_data!!)

    override fun queryKnownIDs(): MutableSet<Long>? {
        val res = mutableSetOf<Long>()
        db.badgeLayoutQueries.selectServerIdsByEventSlug(event_slug = eventSlug).execute { cursor ->
            while (cursor.next().value) {
                val id =
                    cursor.getLong(0) ?: throw RuntimeException("server_id column not available")
                res.add(id)
            }

            QueryResult.Unit
        }

        return res
    }

    override fun insert(jsonobj: JSONObject) {
        val backgroundFilename = processBackground(jsonobj, null)

        db.badgeLayoutQueries.insert(
            background_filename = backgroundFilename,
            event_slug = eventSlug,
            is_default = jsonobj.getBoolean("default"),
            json_data = jsonobj.toString(),
            server_id = jsonobj.getLong("id"),
        )
    }

    override fun update(obj: BadgeLayout, jsonobj: JSONObject) {
        val backgroundFilename = processBackground(jsonobj, obj.background_filename)

        db.badgeLayoutQueries.updateFromJson(
            background_filename = backgroundFilename,
            event_slug = eventSlug,
            is_default = jsonobj.getBoolean("default"),
            json_data = jsonobj.toString(),
            id = obj.id,
        )
    }

    private fun processBackground(jsonobj: JSONObject, oldFilename: String?): String? {
        val remote_filename = jsonobj.optString("background")
        var result: String? = null

        if (remote_filename != null && remote_filename.startsWith("http")) {
            val hash = HashUtils.toSHA1(remote_filename.toByteArray())
            val local_filename = "badgelayout_" + jsonobj.getLong("id") + "_" + hash + ".pdf"
            if (oldFilename != null && oldFilename != local_filename) {
                fileStorage.delete(oldFilename)
                result = null
            }
            if (!fileStorage.contains(local_filename)) {
                try {
                    val file = api.downloadFile(remote_filename)
                    val os = fileStorage.writeStream(local_filename)
                    val `is` = file.response.body!!.byteStream()
                    val buffer = ByteArray(1444)
                    var byteread: Int
                    while ((`is`.read(buffer).also { byteread = it }) != -1) {
                        os.write(buffer, 0, byteread)
                    }
                    `is`.close()
                    os.close()
                    result = local_filename
                } catch (e: ApiException) {
                    // TODO: What to do?
                    e.printStackTrace()
                } catch (e: IOException) {
                    // TODO: What to do?
                    e.printStackTrace()
                    fileStorage.delete(local_filename)
                }
            } else {
                result = local_filename
            }
        } else {
            if (oldFilename != null) {
                fileStorage.delete(oldFilename)
                result = null
            }
        }

        return result
    }

    override fun delete(key: Long) {
        db.badgeLayoutQueries.deleteByServerId(key)
    }

    override fun prepareDelete(obj: BadgeLayout) {
        super.prepareDelete(obj)
        if (obj.background_filename != null) {
            fileStorage.delete(obj.background_filename)
        }
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.badgeLayoutQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<BadgeLayout> =
        db.badgeLayoutQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

}
