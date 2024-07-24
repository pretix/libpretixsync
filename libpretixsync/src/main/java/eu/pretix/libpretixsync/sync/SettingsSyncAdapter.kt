package eu.pretix.libpretixsync.sync

import app.cash.sqldelight.TransactionWithoutReturn
import eu.pretix.libpretixsync.api.PretixApi
import eu.pretix.libpretixsync.sqldelight.Settings
import eu.pretix.libpretixsync.sqldelight.SyncDatabase
import eu.pretix.libpretixsync.sync.SyncManager.ProgressFeedback
import org.json.JSONObject

open class SettingsSyncAdapter(
    db: SyncDatabase,
    eventSlug: String,
    key: String,
    api: PretixApi,
    syncCycleId: String,
    feedback: ProgressFeedback? = null,
) : SqBaseSingleObjectSyncAdapter<Settings>(
    db = db,
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
}
