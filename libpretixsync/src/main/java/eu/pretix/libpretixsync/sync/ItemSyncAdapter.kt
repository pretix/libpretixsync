package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.libpretixsync.sqldelight.Item
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.HashUtils
import eu.pretix.libpretixsync.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

class ItemSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
) : BaseConditionalSyncAdapter<Item, Long>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {

    override fun getResourceName(): String = "items"

    override fun getId(obj: Item): Long = obj.server_id!!

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getJSON(obj: Item): JSONObject = JSONObject(obj.json_data)

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.itemQueries.selectServerIdsByEventSlug(eventSlug).execute { cursor ->
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
        val pictureFilename = processPicture(jsonobj, null)

        db.itemQueries.insert(
            active = jsonobj.optBoolean("active", true),
            admission = jsonobj.optBoolean("admission", false),
            category_id = jsonobj.optLong("category"),
            checkin_text = jsonobj.optString("checkin_text"),
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            picture_filename = pictureFilename,
            position = jsonobj.getLong("position"),
            server_id = jsonobj.getLong("id"),
            ticket_layout_id = null,
            ticket_layout_pretixpos_id = null,
        )
    }

    override fun update(obj: Item, jsonobj: JSONObject) {
        val pictureFilename = processPicture(jsonobj, obj.picture_filename)

        db.itemQueries.updateFromJson(
            active = jsonobj.optBoolean("active", true),
            admission = jsonobj.optBoolean("admission", false),
            category_id = jsonobj.optLong("category"),
            checkin_text = jsonobj.optString("checkin_text"),
            event_slug = eventSlug,
            json_data = jsonobj.toString(),
            picture_filename = pictureFilename,
            position = jsonobj.getLong("position"),
            id = obj.id,
        )
    }

    override fun delete(key: Long) {
        db.itemQueries.deleteByServerId(key)
    }

    private fun processPicture(jsonobj: JSONObject, oldFilename: String?): String? {
        val remote_filename: String = jsonobj.optString("picture")
        var result: String? = null;

        if (remote_filename.startsWith("http")) {
            val hash = HashUtils.toSHA1(remote_filename.toByteArray())
            val local_filename =
                "item_" + jsonobj.getLong("id") + "_" + hash + remote_filename.substring(
                    remote_filename.lastIndexOf(".")
                )
            if (oldFilename != null && oldFilename != local_filename) {
                fileStorage.delete(oldFilename)
                result = null
            }
            if (!fileStorage.contains(local_filename)) {
                try {
                    val file = api.downloadFile(remote_filename)

                    val outStream: OutputStream = fileStorage.writeStream(local_filename)
                    val inStream = file.response.body?.byteStream()

                    if (inStream == null) {
                        outStream.close()
                        throw IOException()
                    }

                    val buffer = ByteArray(1444)
                    var byteread: Int
                    while (inStream.read(buffer).also { byteread = it } != -1) {
                        outStream.write(buffer, 0, byteread)
                    }
                    inStream.close()
                    outStream.close()
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

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.itemQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<Item> =
        db.itemQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

    @Throws(JSONException::class)
    fun standaloneRefreshFromJSON(data: JSONObject) {
        val obj = db.itemQueries.selectByServerId(data.getLong("id")).executeAsOneOrNull()
        val old: JSONObject? = obj?.json_data?.let { JSONObject(it) }

        // Store object
        data.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION)
        data.put("__libpretixsync_syncCycleId", syncCycleId)
        if (old == null) {
            insert(data)
        } else {
            if (!JSONUtils.similar(data, old)) {
                update(obj, data)
            }
        }
    }
}
