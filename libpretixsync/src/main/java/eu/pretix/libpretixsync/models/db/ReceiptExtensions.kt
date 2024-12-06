package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.sqldelight.Receipt
import eu.pretix.libpretixsync.models.Receipt as ReceiptModel

fun Receipt.toModel() =
    ReceiptModel(
        id = id,
        eventSlug = event_slug!!,
        paymentType = ReceiptModel.PaymentType.valueOf(payment_type!!.uppercase()),
        currency = currency,
        orderCode = order_code,
        dateTimeOpened = datetime_opened!!,
        dateTimeClosed = datetime_closed,
        fiscalisationData = fiscalisation_data,
        fiscalisationText = fiscalisation_text,
        fiscalisationQr = fiscalisation_qr,
        isCanceled = canceled,
        isTraining = training,
        isOpen = open_ == true,
        isStarted = started == true,
        isPrinted = printed,
        cashierName = cashier_name,
        cashierNumericId = cashier_numericid,
        cashierUserId = cashier_userid,
        chosenCartId = chosen_cart_id,
        emailTo = email_to,
        closing = closing,
        additionalText = additional_text,
    )
