package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.models.Settings as SettingsModel
import eu.pretix.libpretixsync.sqldelight.Settings
import org.json.JSONObject

fun Settings.toModel() =
    SettingsModel(
        id = id,
        address = address,
        city = city,
        country = country,
        jsonData = json_data,
        name = name,
        pretixposAdditionalReceiptText = pretixpos_additional_receipt_text,
        slug = slug,
        taxId = tax_id,
        vatId = vat_id,
        zipcode = zipcode,
        json = JSONObject(json_data!!),
    )
