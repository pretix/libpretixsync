package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.db.CheckInList
import eu.pretix.libpretixsync.db.Migrations
import eu.pretix.libpretixsync.sqldelight.CheckIn
import eu.pretix.libpretixsync.sqldelight.Item
import eu.pretix.libpretixsync.sqldelight.OrderPosition
import eu.pretix.libpretixsync.sqldelight.ResourceSyncStatus
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.HashUtils
import eu.pretix.libpretixsync.utils.JSONUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import eu.pretix.libpretixsync.sqldelight.Orders as Order

class OrderSyncAdapter(
        db: SyncDatabase,
        fileStorage: FileStorage,
        eventSlug: String,
        private val subEventId: Long?,
        private val withPdfData: Boolean,
        private val isPretixpos: Boolean,
        api: PretixApi,
        syncCylceId: String,
        feedback: ProgressFeedback?,
) : BaseDownloadSyncAdapter<Order, String>(db, api, syncCylceId, eventSlug, fileStorage, feedback) {

    private val itemCache: MutableMap<Long, Item> = HashMap()
    private val checkinCache: MutableMap<Long, MutableList<CheckIn>> = HashMap()
    private val checkinCreateCache: MutableList<CheckIn> = ArrayList()

    private var firstResponseTimestamp: String? = null
    private var lastOrderTimestamp: String? = null
    private var rlm: ResourceSyncStatus? = null

    private fun rlmName(): String {
        return if (withPdfData) {
            "orders_withpdfdata"
        } else {
            "orders"
        }
    }

    override fun download() {
        var completed = false
        try {
            super.download()
            completed = true
        } finally {
            val resourceSyncStatus = db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
                    resource = rlmName(),
                    event_slug = eventSlug,
            ).executeAsOneOrNull()

            // We need to cache the response timestamp of the *first* page in the result set to make
            // sure we don't miss anything between this and the next run.
            //
            // If the download failed, completed will be false. In case this was a full fetch
            // (i.e. no timestamp was stored beforehand) we will still store the timestamp to be
            // able to continue properly.
            if (firstResponseTimestamp != null) {
                if (resourceSyncStatus == null) {
                    val status = if (completed) {
                        "complete"
                    } else {
                        "incomplete:$lastOrderTimestamp"
                    }

                    db.resourceSyncStatusQueries.insert(
                            event_slug = eventSlug,
                            last_modified = firstResponseTimestamp,
                            meta = null,
                            resource = rlmName(),
                            status = status,
                    )
                } else {
                    if (completed) {
                        db.resourceSyncStatusQueries.updateLastModified(
                                last_modified = firstResponseTimestamp,
                                id = resourceSyncStatus.id,
                        )
                    }
                }
            } else if (completed && resourceSyncStatus != null) {
                db.resourceSyncStatusQueries.updateStatus(
                        status = "complete",
                        id = resourceSyncStatus.id,
                )
            } else if (!completed && lastOrderTimestamp != null && resourceSyncStatus != null) {
                db.resourceSyncStatusQueries.updateStatus(
                        status = "incomplete:$lastOrderTimestamp",
                        id = resourceSyncStatus.id,
                )
            }
            lastOrderTimestamp = null
            firstResponseTimestamp = null
        }
    }

    private fun preparePositionObject(jsonobj: JSONObject, orderId: Long, jsonorder: JSONObject, parent: JSONObject?): OrderPosition {
        val jsonName = if (jsonobj.isNull("attendee_name")) "" else jsonobj.optString("attendee_name")
        // TODO: BUG: jsonName can never be null, so parent / jInvoiceAddress is never used
        // Keeping old behaviour for compatibility
        var attendeeName = if (jsonName == null && parent != null && !parent.isNull("attendee_name")) {
            parent.getString("attendee_name")
        } else {
            jsonName
        }
        if (attendeeName == null) {
            try {
                val jInvoiceAddress = jsonorder.getJSONObject("invoice_address")
                if (jInvoiceAddress.isNull("name")) {
                    attendeeName = jInvoiceAddress.getString("name")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        val jsonEmail = if (jsonobj.isNull("attendee_email")) "" else jsonobj.optString("attendee_email")
        // TODO: BUG: jsonEmail can never be null, so parent is never used
        // Keeping old behaviour for compatibility
        val attendeeEmail = if (jsonEmail == null && parent != null && !parent.isNull("attendee_email")) {
            parent.getString("attendee_email")
        } else {
            jsonEmail
        }

        return OrderPosition(
            id = -1,
            attendee_email = attendeeEmail,
            attendee_name = attendeeName,
            item = getItem(jsonobj.getLong("item"))?.id,
            json_data = jsonobj.toString(),
            order_ref = orderId,
            positionid = jsonobj.getLong("positionid"),
            secret = jsonobj.optString("secret"),
            server_id = jsonobj.getLong("id"),
            subevent_id = jsonobj.optLong("subevent"),
            variation_id = jsonobj.optLong("variation"),
        )
    }

    private fun insertPositionObject(jsonobj: JSONObject, orderId: Long, jsonorder: JSONObject, parent: JSONObject?) {
        val posobj = preparePositionObject(jsonobj, orderId, jsonorder, parent)

        val id = db.orderPositionQueries.transactionWithResult {
            db.orderPositionQueries.insert(
                attendee_email = posobj.attendee_email,
                attendee_name = posobj.attendee_name,
                item = posobj.item,
                json_data = posobj.json_data,
                order_ref = posobj.order_ref,
                positionid = posobj.positionid,
                secret = posobj.secret,
                server_id = posobj.server_id,
                subevent_id = posobj.subevent_id,
                variation_id = posobj.variation_id,
            )

            db.compatQueries.getLastInsertedOrderPositionId().executeAsOne()
        }

        afterInsertOrUpdatePositionObject(id, posobj.server_id, jsonobj)
    }

    private fun updatePositionObject(obj: OrderPosition, jsonobj: JSONObject, orderId: Long, jsonorder: JSONObject, parent: JSONObject?) {
        val posobj = preparePositionObject(jsonobj, orderId, jsonorder, parent)

        db.orderPositionQueries.updateFromJson(
            attendee_email = posobj.attendee_email,
            attendee_name = posobj.attendee_name,
            item = posobj.item,
            json_data = posobj.json_data,
            order_ref = posobj.order_ref,
            positionid = posobj.positionid,
            secret = posobj.secret,
            server_id = posobj.server_id,
            subevent_id = posobj.subevent_id,
            variation_id = posobj.variation_id,
            id = obj.id,
        )

        afterInsertOrUpdatePositionObject(obj.id, obj.server_id, jsonobj)
    }

    private fun afterInsertOrUpdatePositionObject(positionId: Long, positionServerId: Long?, jsonobj: JSONObject) {
        val known: MutableMap<Long, CheckIn> = mutableMapOf()
        val checkincache: List<CheckIn>? = checkinCache[positionId]
        if (checkincache != null) {
            for (op in checkincache) {
                if (op.server_id != null && op.server_id > 0) {
                    known[op.server_id] = op
                } else {
                    db.checkInQueries.deleteById(op.id)
                }
            }
        }
        val checkins = jsonobj.getJSONArray("checkins")
        for (i in 0 until checkins.length()) {
            val ci = checkins.getJSONObject(i)
            val listid = ci.getLong("list")
            if (known.containsKey(listid)) {
                val ciobj = known.remove(listid)!!

                db.checkInQueries.updateFromJson(
                    datetime = ISODateTimeFormat.dateTimeParser().parseDateTime(ci.getString("datetime")).toDate(),
                    json_data = ci.toString(),
                    listId = listid,
                    position = positionId,
                    type = ci.optString("type", "entry"),
                    id = ciobj.id,
                )
            } else {
                val ciobj = CheckIn(
                    id = -1,
                    datetime = ISODateTimeFormat.dateTimeParser().parseDateTime(ci.getString("datetime")).toDate(),
                    json_data = ci.toString(),
                    listId = listid,
                    position = positionId,
                    server_id = ci.optLong("id"),
                    type = ci.optString("type", "entry"),
                )
                checkinCreateCache.add(ciobj)
            }
        }
        if (known.size > 0) {
            db.checkInQueries.deleteByIdList(known.values.map { it.id })
        }


        // Images
        if (jsonobj.has("pdf_data")) {
            val pdfdata = jsonobj.getJSONObject("pdf_data")
            if (pdfdata.has("images")) {
                val images = pdfdata.getJSONObject("images")
                updatePdfImages(db, fileStorage, api, positionServerId!!, images)
            }
        }
    }

    override fun afterPage() {
        super.afterPage()

        checkinCreateCache.forEach {
            db.checkInQueries.insert(
                    datetime = it.datetime,
                    json_data = it.json_data,
                    listId = it.listId,
                    position = it.position,
                    server_id = it.server_id,
                    type = it.type,
            )
        }
        checkinCreateCache.clear()
    }


    override fun insert(jsonobj: JSONObject) {
        val json_data = JSONObject(jsonobj.toString())
        json_data.remove("positions")

        val id = db.orderQueries.transactionWithResult {
            db.orderQueries.insert(
                    checkin_attention = jsonobj.optBoolean("checkin_attention"),
                    checkin_text = jsonobj.optString("checkin_text"),
                    code = jsonobj.getString("code"),
                    deleteAfterTimestamp = 0L,
                    email = jsonobj.optString("email"),
                    event_slug = eventSlug,
                    json_data = json_data.toString(),
                    status = jsonobj.getString("status"),
                    valid_if_pending = jsonobj.optBoolean("valid_if_pending", false),
            )
            db.compatQueries.getLastInsertedOrderId().executeAsOne()
        }

        afterInsertOrUpdate(id, jsonobj)
    }

    override fun update(obj: Order, jsonobj: JSONObject) {
        val json_data = JSONObject(jsonobj.toString())
        json_data.remove("positions")

        db.orderQueries.updateFromJson(
                checkin_attention = jsonobj.optBoolean("checkin_attention"),
                checkin_text = jsonobj.optString("checkin_text"),
                code = jsonobj.getString("code"),
                deleteAfterTimestamp = 0L,
                email = jsonobj.optString("email"),
                event_slug = eventSlug,
                json_data = json_data.toString(),
                status = jsonobj.getString("status"),
                valid_if_pending = jsonobj.optBoolean("valid_if_pending", false),
                id = obj.id,
        )

        afterInsertOrUpdate(obj.id, jsonobj)
    }

    private fun afterInsertOrUpdate(orderId: Long, jsonobj: JSONObject) {
        val known: MutableMap<Long, OrderPosition> = mutableMapOf()

        val allPos = db.orderPositionQueries.selectForOrder(orderId).executeAsList()
        for (op in allPos) {
            known[op.server_id!!] = op
        }

        val posarray = jsonobj.getJSONArray("positions")
        val posmap: MutableMap<Long, JSONObject> = java.util.HashMap()
        for (i in 0 until posarray.length()) {
            val posjson = posarray.getJSONObject(i)
            posmap[posjson.getLong("id")] = posjson
        }
        for (i in 0 until posarray.length()) {
            val posjson = posarray.getJSONObject(i)
            posjson.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION)
            posjson.put("__libpretixsync_syncCycleId", syncCycleId)
            val jsonid = posjson.getLong("id")
            var old: JSONObject? = null
            var posobj: OrderPosition? = null
            if (known.containsKey(jsonid)) {
                posobj = known[jsonid]
                old = posobj!!.json_data?.let { JSONObject(it) }
            }
            var parent: JSONObject? = null
            if (!posjson.isNull("addon_to")) {
                parent = posmap[posjson.getLong("addon_to")]
            }
            if (posobj != null) {
                known.remove(jsonid)
                if (!JSONUtils.similar(posjson, old)) {
                    updatePositionObject(posobj, posjson, orderId, jsonobj, parent)
                }
            } else {
                insertPositionObject(posjson, orderId, jsonobj, parent)
            }
        }
        if (known.size > 0) {
            db.orderPositionQueries.deleteByServerIdList(known.values.map { it.server_id })
        }
    }

    override fun deleteUnseen(): Boolean = false

    override fun downloadPage(url: String, isFirstPage: Boolean): JSONObject? {
        if (isFirstPage) {
            rlm = db.resourceSyncStatusQueries.selectByResourceAndEventSlug(
                resource = rlmName(),
                event_slug = eventSlug,
            ).executeAsOneOrNull()
        }
        var is_continued_fetch = false
        var resUrl = url
        if (!resUrl.contains("testmode=")) {
            resUrl += if (resUrl.contains("?")) {
                "&"
            } else {
                "?"
            }
            resUrl += "testmode=false&exclude=downloads&exclude=payment_date&exclude=payment_provider&exclude=fees&exclude=positions.downloads"
            if (!isPretixpos) {
                resUrl += "&exclude=payments&exclude=refunds"
            }
            if (withPdfData) {
                resUrl += "&pdf_data=true"
            }
        }

        val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")
        val cutoff = DateTime().withZone(DateTimeZone.UTC).minus(Duration.standardDays(14))
        var firstrun_params = ""
        try {
            if (subEventId != null && subEventId > 0) {
                firstrun_params = "&subevent_after=" + URLEncoder.encode(formatter.print(cutoff), "UTF-8")
            }
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }

        // On event series, we ignore orders that only affect subevents more than 14 days old.
        // However, we can only do that on the first run, since we'd otherwise miss if e.g. an order
        // that we have in our current database is changed to a date outside that time frame.
        val resourceSyncStatus = rlm
        if (resourceSyncStatus != null) {
            // This resource has been fetched before.
            if (resourceSyncStatus.status != null && resourceSyncStatus.status.startsWith("incomplete:")) {
                // Continuing an interrupted fetch

                // Ordering is crucial here: Only because the server returns the orders in the
                // order of creation we can be sure that we don't miss orders created in between our
                // paginated requests.

                is_continued_fetch = true
                try {
                    if (!resUrl.contains("created_since")) {
                        resUrl += "&ordering=datetime&created_since=" + URLEncoder.encode(resourceSyncStatus.status.substring(11), "UTF-8") + firstrun_params
                    }
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            } else {
                // Diff to last time

                // Ordering is crucial here: Only because the server returns the orders in the
                // order of modification we can be sure that we don't miss orders created in between our
                // paginated requests. If an order were to be modified between our fetch of page 1
                // and 2 that originally wasn't part of the result set, we won't see it (as it will
                // be inserted on page 1), but we'll see it the next time, and we will se some
                // duplicates on page 2, but we don't care. The important part is that nothing gets
                // lost "between the pages". If an order of page 2 gets modified and moves to page
                // one while we fetch page 2, again, we won't see it and we'll see some duplicates,
                // but the next sync will fix it since we always fetch our diff compared to the time
                // of the first page.

                try {
                    if (!resUrl.contains("modified_since")) {
                        resUrl += "&ordering=-last_modified&modified_since=" + URLEncoder.encode(resourceSyncStatus.last_modified, "UTF-8")
                    }
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
            }
        } else {
            if (!resUrl.contains("subevent_after")) {
                resUrl += firstrun_params
            }
        }

        val apiResponse = api.fetchResource(resUrl)
        if (isFirstPage && !is_continued_fetch) {
            firstResponseTimestamp = apiResponse.response.header("X-Page-Generated")
        }
        val d = apiResponse.data
        if (apiResponse.response.code == 200) {
            try {
                val res = d!!.getJSONArray("results")
                if (res.length() > 0) {
                    lastOrderTimestamp = res.getJSONObject(res.length() - 1).getString("datetime")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return d
    }

    private fun getItem(id: Long): Item? {
        if (itemCache.size == 0) {
            val items = db.itemQueries.selectAll().executeAsList()
            for (item in items) {
                itemCache.put(item.server_id, item)
            }
        }
        return itemCache[id]
    }

    override fun queryKnownIDs(): MutableSet<String>? = null

    override fun queryKnownObjects(ids: Set<String>): MutableMap<String, Order> {
        checkinCache.clear()

        if (ids.isEmpty()) {
            return mutableMapOf()
        }

        val allCheckins = db.checkInQueries.selectForOrders(ids).executeAsList()

        for (c in allCheckins) {
            val pk = c.position!!
            if (checkinCache.containsKey(pk)) {
                checkinCache[pk]!!.add(c)
            } else {
                val l: MutableList<CheckIn> = java.util.ArrayList()
                l.add(c)
                checkinCache[pk] = l
            }
        }

        return super.queryKnownObjects(ids)
    }

    override fun runBatch(parameterBatch: List<String>): List<Order> {
        // pretix guarantees uniqueness of CODE within an organizer account, so we don't need
        // to filter by EVENT_SLUG. This is good, because SQLite tends to build a very stupid
        // query plan otherwise if statistics are not up to date (using the EVENT_SLUG index
        // instead of using the CODE index)
        return db.orderQueries.selectByCodeList(parameterBatch).executeAsList()
    }

    override fun getKnownCount(): Long {
        return db.orderQueries.countForEventSlug(eventSlug).executeAsOne()
    }

    override fun autoPersist(): Boolean {
        // This differs from the requery-version, since the previous updateObject() mechanism has
        // been replaced with insert() / update() methods.
        // The original OrderSyncAdapter inserted the Order as part of updateObject().
        // TODO: Review API once more adapters have been migrated
        return true
    }

    override fun getResourceName(): String {
        return "orders"
    }

    override fun getId(obj: JSONObject): String {
        return obj.getString("code")
    }

    override fun getId(obj: Order): String {
        return obj.code!!
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.orderQueries.transaction(false, body)
    }

    override fun getJSON(obj: Order): JSONObject {
        return JSONObject(obj.json_data)
    }

    override fun delete(key: String) {
        db.orderQueries.deleteByCode(key)
    }

    fun standaloneRefreshFromJSON(data: JSONObject) {
        val order = db.orderQueries.selectByCode(data.getString("code")).executeAsOneOrNull()

        var old: JSONObject? = null
        if (order?.id != null) {
            old = JSONObject(order.json_data)
            if (!old.has("positions")) {
                val pos = JSONArray()
                val positions = db.orderPositionQueries.selectForOrder(order.id).executeAsList()
                for (p in positions) {
                    pos.put(JSONObject(p.json_data))
                }
                old.put("positions", pos)
            }
        }

        // Warm up cache
        val ids = mutableSetOf<String>()
        ids.add(data.getString("code"))
        queryKnownObjects(ids)
        // Store object
        data.put("__libpretixsync_dbversion", Migrations.CURRENT_VERSION)
        data.put("__libpretixsync_syncCycleId", syncCycleId)

        if (order == null) {
            insert(data)
        } else {
            if (!JSONUtils.similar(data, old)) {
                update(order, data)
            }
        }

        for (c in checkinCreateCache) {
            db.checkInQueries.insert(
                datetime = c.datetime,
                json_data = c.json_data,
                listId = c.listId,
                position = c.position,
                server_id = c.server_id,
                type = c.type,
            )
        }

        checkinCreateCache.clear()
    }

    companion object {
        fun updatePdfImages(db: SyncDatabase, fileStorage: FileStorage, api: PretixApi, serverId: Long, images: JSONObject) {
            val seen_etags: MutableSet<String> = HashSet()
            val it = images.keys()
            while (it.hasNext()) {
                val k = it.next() as String
                val remote_filename = images.optString(k)
                if (remote_filename == null || !remote_filename.startsWith("http")) {
                    continue
                }
                var etag = HashUtils.toSHA1(remote_filename.toByteArray())
                if (remote_filename.contains("#etag=")) {
                    etag = remote_filename.split("#etag=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                }
                val local_filename = "pdfimage_$etag.bin"
                seen_etags.add(etag)

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
                    } catch (e: ApiException) {
                        // TODO: What to do?
                        e.printStackTrace()
                    } catch (e: IOException) {
                        // TODO: What to do?
                        e.printStackTrace()
                        fileStorage.delete(local_filename)
                    }
                }

                var cpi = db.cachedPdfImageQueries.selectForOrderPositionAndKey(
                        order_position_server_id = serverId,
                        key = k,
                ).executeAsOneOrNull()

                if (cpi == null) {
                    db.cachedPdfImageQueries.insert(
                            etag = etag,
                            key = k,
                            order_position_server_id = serverId,
                    )
                } else {
                    db.cachedPdfImageQueries.updateEtag(
                            etag = etag,
                            id = cpi.id,
                    )
                }
            }

            db.cachedPdfImageQueries.deleteUnseen(
                    order_position_server_id = serverId,
                    seen_etags = seen_etags,
            )
        }
    }
}
