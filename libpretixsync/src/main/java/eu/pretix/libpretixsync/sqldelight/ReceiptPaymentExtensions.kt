package eu.pretix.libpretixsync.sqldelight

import org.json.JSONObject

fun ReceiptPayment.toJSON(): JSONObject {
    val jo = JSONObject()
    jo.put("id", id)
    jo.put("payment_type", payment_type)
    jo.put("status", status)
    jo.put("amount", amount)
    jo.put(
        "payment_data",
        if ((detailsJson == null || detailsJson == "null")) JSONObject() else JSONObject(detailsJson)
    )
    return jo
}