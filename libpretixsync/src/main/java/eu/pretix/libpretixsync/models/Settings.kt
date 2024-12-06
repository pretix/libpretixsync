package eu.pretix.libpretixsync.models

import org.json.JSONObject

data class Settings(
    val id: Long,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val jsonData: String? = null,
    val name: String? = null,
    val pretixposAdditionalReceiptText: String? = null,
    val slug: String? = null,
    val taxId: String? = null,
    val vatId: String? = null,
    val zipcode: String? = null,
    val json: JSONObject = JSONObject(),
)
