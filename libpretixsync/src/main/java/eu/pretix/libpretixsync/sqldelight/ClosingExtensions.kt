package eu.pretix.libpretixsync.sqldelight

import org.json.JSONObject
import java.math.RoundingMode
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Closing.toJSON(): JSONObject {
    val df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    val jo = JSONObject()
    jo.put("closing_id", id)
    jo.put("first_receipt", first_receipt)
    jo.put("last_receipt", last_receipt)
    jo.put("payment_sum", payment_sum?.setScale(2, RoundingMode.HALF_UP))
    jo.put("payment_sum_cash", payment_sum_cash?.setScale(2, RoundingMode.HALF_UP))
    jo.put("cash_counted", cash_counted?.setScale(2, RoundingMode.HALF_UP))
    jo.put("datetime", df.format(datetime))
    jo.put("invoice_settings", invoice_settings)
    jo.put("cashier", cashier_numericid)
    jo.put("data", if (json_data != null) JSONObject(json_data) else JSONObject())
    return jo
}
