package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.models.Item as ItemModel
import eu.pretix.libpretixsync.db.MediaPolicy
import eu.pretix.libpretixsync.db.ReusableMediaType
import eu.pretix.libpretixsync.sqldelight.Item
import eu.pretix.libpretixsync.sqldelight.isGenerateTickets
import eu.pretix.libpretixsync.utils.I18nString
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal

fun Item.toModel(): ItemModel {
    val json = JSONObject(this.json_data)

    return ItemModel(
        id = this.id,
        serverId = this.server_id,
        active = this.active,
        admission = this.admission,
        name = parseName(json),
        nameI18n = json.getJSONObject("name"),
        description = parseDescription(json),
        descriptionI18n = json.optJSONObject("description") ?: JSONObject(),
        internalName = parseInternalName(json),
        isPersonalized = parseIsPersonalized(json, this.admission),
        hasVariations = parseHasVariations(json),
        hasDynamicValidityWithCustomStart = parseHasDynamicValidityWithCustomStart(json),
        hasDynamicValidityWithTimeOfDay = parseHasDynamicValidityWithTimeOfDay(json),
        dynamicValidityDayLimit = parseDynamicValidityDayLimit(json),
        categoryServerId = this.category_id,
        checkInText = this.checkin_text,
        eventSlug = this.event_slug,
        pictureFilename = this.picture_filename,
        position = this.position,
        ticketLayoutServerId = this.ticket_layout_id,
        ticketLayoutPretixPosId = this.ticket_layout_pretixpos_id,
        requireVoucher = parseRequireVoucher(json),
        hideWithoutVoucher = parseHideWithoutVoucher(json),
        isGiftcard = parseIsGiftcard(json),
        requireBundling = parseRequireBundling(json),
        taxRuleId = parseTaxRuleId(json),
        defaultPrice = parseDefaultPrice(json),
        hasFreePrice = parseHasFreePrice(json),
        mediaPolicy = parseMediaPolicy(json),
        mediaType = parseMediaType(json),
        generateTickets = this.isGenerateTickets,
        variations = json.getJSONArray("variations"),
        bundles = json.getJSONArray("bundles"),
        addons = json.getJSONArray("addons"),
        salesChannels = json.optJSONArray("sales_channels"),
        checkInAttention = json.optBoolean("checkin_attention", false),
    )
}

private fun parseInternalName(json: JSONObject): String {
    return try {
        val internal: String = json.optString("internal_name")
        if (internal != null && !internal.isEmpty() && "null" != internal) {
            internal
        } else I18nString.toString(json.getJSONObject("name"))
    } catch (e: JSONException) {
        e.printStackTrace()
        ""
    }
}

private fun parseName(json: JSONObject): String {
    return try {
        I18nString.toString(json.getJSONObject("name"))
    } catch (e: JSONException) {
        e.printStackTrace()
        ""
    }
}

private fun parseDescription(json: JSONObject): String {
    return try {
        if (!json.isNull("description")) {
            I18nString.toString(json.getJSONObject("description")) ?: ""
        } else {
            ""
        }
    } catch (e: JSONException) {
        e.printStackTrace()
        ""
    }
}

private fun parseHasVariations(json: JSONObject): Boolean {
    return try {
        json.getBoolean("has_variations")
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}

private fun parseHasDynamicValidityWithCustomStart(jo: JSONObject): Boolean {
    return try {
        if (jo.optString("validity_mode", "") != "dynamic") {
            false
        } else jo.optBoolean("validity_dynamic_start_choice", false)
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}

private fun parseHasDynamicValidityWithTimeOfDay(jo: JSONObject): Boolean {
    return try {
        if (!jo.isNull("validity_dynamic_duration_months") && jo.optLong(
                "validity_dynamic_duration_months",
                0
            ) > 0 || !jo.isNull("validity_dynamic_duration_days") && jo.optLong(
                "validity_dynamic_duration_days",
                0
            ) > 0
        ) {
            false
        } else true
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}

private fun parseDynamicValidityDayLimit(jo: JSONObject): Long? {
    return try {
        if (jo.has("validity_dynamic_start_choice_day_limit") && !jo.isNull("validity_dynamic_start_choice_day_limit")) {
            jo.getLong("validity_dynamic_start_choice_day_limit")
        } else null
    } catch (e: JSONException) {
        e.printStackTrace()
        null
    }
}

private fun parseIsPersonalized(j: JSONObject, admission: Boolean): Boolean {
    return try {
        if (j.has("personalized")) {
            j.getBoolean("personalized")
        } else {
            admission
        }
    } catch (e: JSONException) {
        e.printStackTrace()
        true
    }
}

private fun parseRequireVoucher(json: JSONObject): Boolean {
    return try {
        json.getBoolean("require_voucher")
    } catch (e: JSONException) {
        e.printStackTrace()
        true
    }
}

private fun parseHideWithoutVoucher(json: JSONObject): Boolean {
    return try {
        json.getBoolean("hide_without_voucher")
    } catch (e: JSONException) {
        e.printStackTrace()
        true
    }
}

private fun parseIsGiftcard(json: JSONObject): Boolean {
    return try {
        json.getBoolean("issue_giftcard")
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}

private fun parseTaxRuleId(json: JSONObject): Long {
    return try {
        json.optLong("tax_rule")
    } catch (e: JSONException) {
        e.printStackTrace()
        0
    }
}

private fun parseDefaultPrice(json: JSONObject): BigDecimal {
    return try {
        BigDecimal(json.getString("default_price"))
    } catch (e: JSONException) {
        e.printStackTrace()
        BigDecimal(0.00)
    }
}

private fun parseHasFreePrice(json: JSONObject): Boolean {
    return try {
        if (json.isNull("free_price")) {
            false
        } else json.getBoolean("free_price")
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}

private fun parseMediaPolicy(json: JSONObject): MediaPolicy {
    return try {
        val mp: String = json.optString("media_policy") ?: return MediaPolicy.NONE
        if (mp == "reuse") return MediaPolicy.REUSE
        if (mp == "new") return MediaPolicy.NEW
        if (mp == "reuse_or_new") MediaPolicy.REUSE_OR_NEW else MediaPolicy.NONE
    } catch (e: JSONException) {
        e.printStackTrace()
        MediaPolicy.NONE
    }
}

private fun parseMediaType(json: JSONObject): ReusableMediaType {
    return try {
        val mp: String = json.optString("media_type") ?: return ReusableMediaType.NONE
        if (mp == "barcode") return ReusableMediaType.BARCODE
        if (mp == "nfc_uid") return ReusableMediaType.NFC_UID
        if (mp == "nfc_mf0aes") ReusableMediaType.NFC_MF0AES else ReusableMediaType.UNSUPPORTED
    } catch (e: JSONException) {
        e.printStackTrace()
        ReusableMediaType.NONE
    }
}

private fun parseRequireBundling(json: JSONObject): Boolean {
    return try {
        json.getBoolean("require_bundling")
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}
