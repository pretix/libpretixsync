package eu.pretix.libpretixsync.sqldelight

import org.json.JSONObject

val Settings.fiscalJSON: JSONObject
    get() {
        val jsonobj = JSONObject(json_data)
        val j = JSONObject()
        j.put("slug", slug)
        j.put("invoice_address_from_name", jsonobj.optString("invoice_address_from_name"))
        j.put("invoice_address_from", jsonobj.optString("invoice_address_from"))
        j.put("invoice_address_from_zipcode", jsonobj.optString("invoice_address_from_zipcode"))
        j.put("invoice_address_from_city", jsonobj.optString("invoice_address_from_city"))
        j.put("invoice_address_from_country", jsonobj.optString("invoice_address_from_country"))
        j.put("invoice_address_from_tax_id", jsonobj.optString("invoice_address_from_tax_id"))
        j.put("invoice_address_from_vat_id", jsonobj.optString("invoice_address_from_vat_id"))
        return j
    }
