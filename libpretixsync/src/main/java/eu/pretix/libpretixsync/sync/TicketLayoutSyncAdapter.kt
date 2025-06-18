package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Item
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sqldelight.TicketLayout
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.HashUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class TicketLayoutSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback?,
    private val salesChannel: String = "pretixpos",
) : BaseDownloadSyncAdapter<TicketLayout, Long>(
    db = db,
    api = api,
    syncCycleId = syncCycleId,
    eventSlug = eventSlug,
    fileStorage = fileStorage,
    feedback = feedback,
) {
    override fun getResourceName(): String = "ticketlayouts"

    override fun getJSON(obj: TicketLayout): JSONObject = JSONObject(obj.json_data!!)

    override fun getId(obj: JSONObject): Long = obj.getLong("id")

    override fun getId(obj: TicketLayout): Long = obj.server_id!!

    override fun queryKnownIDs(): MutableSet<Long> {
        val res = mutableSetOf<Long>()
        db.ticketLayoutQueries.selectServerIdsByEventSlug(eventSlug).execute { cursor ->
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
        val serverId = jsonobj.getLong("id")

        // Iterate over all items this layout is assigned to
        processItems(serverId, jsonobj.getJSONArray("item_assignments"))

        val backgroundFilename = processBackground(jsonobj, null)

        db.ticketLayoutQueries.insert(
            background_filename = backgroundFilename,
            event_slug = eventSlug,
            is_default = jsonobj.getBoolean("default"),
            json_data = jsonobj.toString(),
            server_id = serverId,
        )
    }

    override fun update(obj: TicketLayout, jsonobj: JSONObject) {
        val serverId = jsonobj.getLong("id")

        // Iterate over all items this layout is assigned to
        processItems(serverId, jsonobj.getJSONArray("item_assignments"))

        val backgroundFilename = processBackground(jsonobj, null)

        db.ticketLayoutQueries.updateFromJson(
            background_filename = backgroundFilename,
            event_slug = eventSlug,
            is_default = jsonobj.getBoolean("default"),
            json_data = jsonobj.toString(),
            id = obj.id,
        )
    }

    private fun processItems(serverId: Long, assignmentarr: JSONArray) {
        // itemids will be a list of all item IDs where we *could* assign this to through either
        // channel
        val itemids_web: MutableList<Long> = ArrayList()
        val itemids_pretixpos: MutableList<Long> = ArrayList()

        for (i in 0 until assignmentarr.length()) {
            val item = assignmentarr.getJSONObject(i).getLong("item")
            var sc = assignmentarr.getJSONObject(i).optString("sales_channel", "web")
            if (sc == null) {
                sc = "web"
            }

            if (sc == "web") {
                itemids_web.add(item)

                val itemobj = db.itemQueries.selectByServerId(item).executeAsOneOrNull()
                if (itemobj != null) {
                    db.itemQueries.updateTicketLayoutId(
                        ticket_layout_id = serverId,
                        id = itemobj.id
                    )
                }
            } else if (sc == salesChannel) {
                itemids_pretixpos.add(item)

                val itemobj = db.itemQueries.selectByServerId(item).executeAsOneOrNull()
                if (itemobj != null) {
                    db.itemQueries.updateTicketLayoutPretixposId(
                        ticket_layout_pretixpos_id = serverId,
                        id = itemobj.id
                    )
                }
            }
        }

        val items_to_remove_web: List<Item> = if (itemids_web.isNotEmpty()) {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            db.itemQueries.getWithOutdatedTicketLayoutId(
                server_id_not_in = itemids_web,
                ticket_layout_id = serverId,
            ).executeAsList()
        } else {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            db.itemQueries.selectByTicketLayoutId(
                ticket_layout_id = serverId,
            ).executeAsList()
        }
        for (item in items_to_remove_web) {
            db.itemQueries.updateTicketLayoutId(
                ticket_layout_id = null,
                id = item.id,
            )
        }

        val items_to_remove_pretixpos: List<Item> = if (itemids_pretixpos.isNotEmpty()) {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            db.itemQueries.getWithOutdatedTicketLayoutPretixposId(
                server_id_not_in = itemids_pretixpos,
                ticket_layout_pretixpos_id = serverId,
            ).executeAsList()
        } else {
            // Look if there are any items in the local database assigned to this layout even though
            // they should not be any more.
            db.itemQueries.selectByTicketLayoutPretixposId(
                ticket_layout_pretixpos_id = serverId,
            ).executeAsList()
        }
        for (item in items_to_remove_pretixpos) {
            db.itemQueries.updateTicketLayoutPretixposId(
                ticket_layout_pretixpos_id = null,
                id = item.id,
            )
        }
    }

    private fun processBackground(jsonobj: JSONObject, oldFilename: String?): String? {
        val remote_filename = jsonobj.optString("background")
        var result: String? = null

        if (remote_filename != null && remote_filename.startsWith("http")) {
            val hash = HashUtils.toSHA1(remote_filename.toByteArray())
            val local_filename = "ticketlayout_" + jsonobj.getLong("id") + "_" + hash + ".pdf"
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
        db.ticketLayoutQueries.deleteByServerId(key)
    }

    override fun prepareDelete(obj: TicketLayout) {
        super.prepareDelete(obj)
        if (obj.background_filename != null) {
            fileStorage.delete(obj.background_filename)
        }
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.ticketLayoutQueries.transaction(false, body)
    }

    override fun runBatch(parameterBatch: List<Long>): List<TicketLayout> =
        db.ticketLayoutQueries.selectByServerIdListAndEventSlug(
            server_id = parameterBatch,
            event_slug = eventSlug,
        ).executeAsList()

}
