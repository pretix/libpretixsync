package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import eu.pretix.libpretixsync.api.ApiException
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Settings
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import eu.pretix.libpretixsync.utils.HashUtils
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

open class SettingsSyncAdapter(
    db: SyncDatabase,
    fileStorage: FileStorage,
    eventSlug: String,
    key: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback? = null,
) : BaseSingleObjectSyncAdapter<Settings>(
    db = db,
    fileStorage = fileStorage,
    eventSlug = eventSlug,
    key = key,
    api = api,
    syncCycleId = syncCycleId,
    feedback = feedback,
) {
    override fun getKnownObject(): Settings? {
        val known = db.settingsQueries.selectBySlug(eventSlug).executeAsList()

        return if (known.isEmpty()) {
            null
        } else if (known.size == 1) {
            known[0]
        } else {
            // What's going on here? Let's delete and re-fetch
            db.settingsQueries.deleteByEventSlug(eventSlug)
            null
        }
    }

    override fun getResourceName(): String = "settings"

    override fun getUrl(): String = api.eventResourceUrl(eventSlug, "settings")

    override fun getJSON(obj: Settings): JSONObject = JSONObject(obj.json_data!!)

    override fun insert(jsonobj: JSONObject) {
        var jsonobj = jsonobj
        listOf("pretixkiosk_screensaver_image").forEach { fieldName ->
            jsonobj = processAndUpdateJSONdataWithPicutre(jsonobj, fieldName, null)
        }

        db.settingsQueries.insert(
            slug = eventSlug,
            address = jsonobj.optString("invoice_address_from"),
            city = jsonobj.optString("invoice_address_from_city"),
            country = jsonobj.optString("invoice_address_from_country"),
            json_data = jsonobj.toString(),
            name = jsonobj.optString("invoice_address_from_name"),
            pretixpos_additional_receipt_text = jsonobj.optString("pretixpos_additional_receipt_text"),
            tax_id = jsonobj.optString("invoice_address_from_tax_id"),
            vat_id = jsonobj.optString("invoice_address_from_vat_id"),
            zipcode = jsonobj.optString("invoice_address_from_zipcode")
        )
    }

    override fun update(obj: Settings, jsonobj: JSONObject) {
        var jsonobj = jsonobj
        val objjsondata = JSONObject(obj.json_data)
        listOf("pretixkiosk_screensaver_image").forEach { fieldName ->
            jsonobj = processAndUpdateJSONdataWithPicutre(jsonobj, fieldName, if (objjsondata.isNull(fieldName)) null else objjsondata.getString(fieldName))
        }

        db.settingsQueries.updateFromJson(
            address = jsonobj.optString("invoice_address_from"),
            city = jsonobj.optString("invoice_address_from_city"),
            country = jsonobj.optString("invoice_address_from_country"),
            json_data = jsonobj.toString(),
            name = jsonobj.optString("invoice_address_from_name"),
            pretixpos_additional_receipt_text = jsonobj.optString("pretixpos_additional_receipt_text"),
            tax_id = jsonobj.optString("invoice_address_from_tax_id"),
            vat_id = jsonobj.optString("invoice_address_from_vat_id"),
            zipcode = jsonobj.optString("invoice_address_from_zipcode"),
            slug = obj.slug,
        )
    }

    override fun runInTransaction(body: TransactionWithoutReturn.() -> Unit) {
        db.settingsQueries.transaction(false, body)
    }

    private fun processAndUpdateJSONdataWithPicutre(jsonobj: JSONObject, fieldName: String, oldFilename: String?): JSONObject {
        if (jsonobj.has(fieldName)) {
            val pictureFilename = processPicture(jsonobj, fieldName, oldFilename)

            jsonobj.put(fieldName, pictureFilename)
        }

        return jsonobj
    }

    private fun processPicture(jsonobj: JSONObject, fieldName: String, oldFilename: String?): String? {
        val remote_filename: String = jsonobj.optString(fieldName)
        var result: String? = null;

        if (remote_filename.startsWith("http")) {
            val hash = HashUtils.toSHA1(remote_filename.toByteArray())
            val local_filename =
                "settings_" + fieldName + "_" + hash + remote_filename.substring(
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
}
