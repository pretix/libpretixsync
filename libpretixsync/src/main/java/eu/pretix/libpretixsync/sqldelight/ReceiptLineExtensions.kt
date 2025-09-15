package eu.pretix.libpretixsync.sqldelight

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

fun ReceiptLine.toJSON(): JSONObject {
    val jo = JSONObject()
    jo.put("id", id)
    jo.put("type", type)
    jo.put("position_id", positionid)
    jo.put("canceled", canceled)
    jo.put("canceled_because_of_receipt", canceled_because_of_receipt)
    jo.put("price_calculated_from_net", price_calculated_from_net)
    jo.put("listed_price", listed_price?.setScale(2, RoundingMode.HALF_UP))
    jo.put("price_after_voucher", price_after_voucher?.setScale(2, RoundingMode.HALF_UP))
    jo.put("custom_price_input", custom_price_input?.setScale(2, RoundingMode.HALF_UP))
    jo.put("voucher_code", voucher_code)
    jo.put("price", price?.setScale(2, RoundingMode.HALF_UP))
    jo.put("tax_rate", tax_rate?.setScale(2, RoundingMode.HALF_UP))
    jo.put("tax_value", tax_value?.setScale(2, RoundingMode.HALF_UP) ?: "0.00")
    jo.put("tax_rule", tax_rule ?: JSONObject.NULL)
    jo.put("tax_code", tax_code ?: JSONObject.NULL)
    jo.put("secret", secret)
    jo.put("seat", seat_guid ?: JSONObject.NULL)
    jo.put("subevent", subevent_id)
    jo.put(
        "event_date_from",
        if (event_date_from != null && event_date_from.length > 5) event_date_from else JSONObject.NULL,
    )
    jo.put(
        "event_date_to",
        if (event_date_to != null && event_date_to.length > 5) event_date_to else JSONObject.NULL,
    )
    jo.put(
        "subevent_text",
        if (subevent_text != null && subevent_text.length > 0 && subevent_text != "null") subevent_text else JSONObject.NULL,
    )
    jo.put("item", if (item_id != null && item_id != 0L) item_id else JSONObject.NULL)
    jo.put("variation", variation_id)
    jo.put("answers", answers)
    jo.put("sale_text", sale_text)
    jo.put("addon_to", addon_to ?: JSONObject.NULL)
    jo.put("is_bundled", is_bundled)
    jo.put("attendee_name", attendee_name)
    jo.put("attendee_email", attendee_email)
    jo.put("attendee_company", attendee_company)
    jo.put("attendee_street", attendee_street)
    jo.put("attendee_zipcode", attendee_zipcode)
    jo.put("attendee_city", attendee_city)
    jo.put("attendee_country", attendee_country)
    jo.put("requested_valid_from", requested_valid_from)
    jo.put("use_reusable_medium", use_reusable_medium)
    jo.put("gift_card", gift_card_id)
    jo.put("gift_card_secret", gift_card_secret)
    return jo
}
