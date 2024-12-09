package eu.pretix.libpretixsync.sqldelight

import eu.pretix.libpretixsync.utils.I18nString
import org.json.JSONException
import org.json.JSONObject

val Item.name: String
    get() {
        val json = JSONObject(json_data)
        return try {
            I18nString.toString(json.getJSONObject("name"))
        } catch (e: JSONException) {
            e.printStackTrace()
            ""
        }
    }

val Item.minPerOrder: Int?
    get() {
        val json = JSONObject(json_data)
        return try {
            if (json.isNull("min_per_order")) null else json.optInt("min_per_order")
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

val Item.maxPerOrder: Int?
    get() {
        val json = JSONObject(json_data)
        try {
            if (json.isNull("max_per_order")) return null;
            return json.optInt("max_per_order");
        } catch (e: JSONException) {
            e.printStackTrace();
            return null;
        }
    }

val Item.isGenerateTickets: Boolean
    get() = try {
        val json = JSONObject(json_data)
        if (json.isNull("generate_tickets")) {
            true
        } else json.getBoolean("generate_tickets")
    } catch (e: JSONException) {
        e.printStackTrace()
        true
    }
