package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.ReceiptLine
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import org.json.JSONArray
import eu.pretix.libpretixsync.models.ReceiptLine as ReceiptLineModel

fun ReceiptLine.toModel() =
    ReceiptLineModel(
        id = id,
        positionId = positionid!!,
        type = ReceiptLineModel.Type.valueOf(type!!),
        price = price!!,
        listedPrice = listed_price,
        priceAfterVoucher = price_after_voucher,
        customPriceInput = custom_price_input,
        cartId = cart_id,
        canceled = canceled,
        canceledBecauseOfReceipt = canceled_because_of_receipt ?: false,
        saleText = sale_text,
        isBundled = is_bundled ?: false,
        addonTo = addon_to,
        remoteError = remote_error,
        voucherCode = voucher_code,
        useReusableMedium = use_reusable_medium,
        taxRate = tax_rate,
        taxRule = tax_rule,
        taxValue = tax_value,
        eventDateFrom = SafeOffsetDateTimeMapper.decode(event_date_from),
        eventDateTo = SafeOffsetDateTimeMapper.decode(event_date_to),
        subEventServerId = subevent_id,
        subEventText = subevent_text,
        itemServerId = item_id,
        variationServerId = variation_id,
        requestedValidFrom = requested_valid_from,
        attendeeCity = attendee_city,
        attendeeCompany = attendee_company,
        attendeeCountry = attendee_country,
        attendeeEmail = attendee_email,
        attendeeName = attendee_name,
        attendeeStreet = attendee_street,
        attendeeZipcode = attendee_zipcode,
        seatGuid = seat_guid,
        seatName = seat_name,
        answers = JSONArray(answers ?: "[]"),
        giftCardId = gift_card_id,
        giftCardSecret = gift_card_secret,
        priceCalculatedFromNet = price_calculated_from_net == true,
    )
