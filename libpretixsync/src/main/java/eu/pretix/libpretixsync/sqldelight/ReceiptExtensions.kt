package eu.pretix.libpretixsync.sqldelight

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun Receipt.toJSON(): JSONObject {
    val tz = TimeZone.getTimeZone("UTC")
    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    df.timeZone = tz
    val jo = JSONObject()
    jo.put("receipt_id", id)
    jo.put("event", if (event_slug != null) event_slug else JSONObject.NULL)
    jo.put("order", if (order_code != null) order_code else JSONObject.NULL)
    jo.put(
        "order_full",
        if (order_code != null) event_slug?.uppercase(Locale.getDefault()) + "-" + order_code else "-",
    )
    jo.put("open", this.open_)
    jo.put("payment_type", payment_type)
    jo.put(
        "datetime_opened",
        if (datetime_opened != null) df.format(datetime_opened) else JSONObject.NULL,
    )
    jo.put(
        "datetime_closed",
        if (datetime_closed != null) df.format(datetime_closed) else JSONObject.NULL,
    )
    jo.put("closing_id", closing)
    jo.put("canceled", canceled)
    jo.put("currency", currency)
    jo.put("printed", printed)
    jo.put("email_to", email_to)
    jo.put(
        "payment_data",
        if (payment_data == null || payment_data == "null" || payment_data.isEmpty()) {
            JSONObject()
        } else {
            JSONObject(
                payment_data,
            )
        },
    )
    jo.put(
        "fiscalisation_data",
        if (fiscalisation_data == null || fiscalisation_data == "null" || fiscalisation_data.isEmpty()) {
            JSONObject()
        } else {
            JSONObject(
                fiscalisation_data,
            )
        },
    )
    jo.put(
        "fiscalisation_text",
        if (fiscalisation_text == null || fiscalisation_text == "null" || fiscalisation_text.isEmpty()) "" else fiscalisation_text,
    )
    jo.put(
        "fiscalisation_qr",
        if (fiscalisation_qr == null || fiscalisation_qr == "null" || fiscalisation_qr.isEmpty()) "" else fiscalisation_qr,
    )
    jo.put("cashier", cashier_numericid)
    jo.put("training", training)
    jo.put("additional_text", additional_text)
    jo.put("invoice_name_parts", if (invoice_name_parts != null) invoice_name_parts else JSONObject.NULL)
    jo.put("order_email", if (order_email != null) order_email else JSONObject.NULL)
    jo.put("order_phone", if (order_phone != null) order_phone else JSONObject.NULL)
    return jo
}
