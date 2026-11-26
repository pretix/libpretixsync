package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.db.QuestionOption
import eu.pretix.libpretixsync.sqldelight.Closing
import eu.pretix.libpretixsync.sqldelight.Questionnaire
import eu.pretix.libpretixsync.sqldelight.SafeOffsetDateTimeMapper
import eu.pretix.libpretixsync.utils.I18nString
import okhttp3.internal.toImmutableList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.TimeZone
import eu.pretix.libpretixsync.models.Questionnaire as QuestionnaireModel

fun Questionnaire.toModel(): QuestionnaireModel {
    val json = JSONObject(this.json_data!!)

    val jRows = json.getJSONArray("rows")
    val rows = mutableListOf<QuestionnaireModel.RowSpec>()
    for (i in 0 until jRows.length()) {
        val jRow = jRows.getJSONObject(i)
        val jDatafield = jRow.getJSONObject("datafield")

        val datafield = QuestionnaireModel.DataField(
            serverId = if (jDatafield.isNull("id")) 0L else jDatafield.getLong("id"),
            identifier = jDatafield.getString("identifier"),
            showDuringCheckin = jDatafield.getBoolean("show_during_checkin"),
            printOnInvoice = jDatafield.getBoolean("print_on_invoice"),
            printOnInvoiceLabel = if (jDatafield.isNull("print_on_invoice_label")) null else I18nString.toString(jDatafield.getJSONObject("print_on_invoice_label")) ?: "",
            validNumberMin = parseBigDecimal(jDatafield, "valid_number_min"),
            validNumberMax = parseBigDecimal(jDatafield, "valid_number_max"),
            validDatetimeMin = SafeOffsetDateTimeMapper.decode(jDatafield, "valid_datetime_min"),
            validDatetimeMax = SafeOffsetDateTimeMapper.decode(jDatafield, "valid_datetime_max"),
            validDateMin = parseLocalDate(jDatafield, "valid_date_min"),
            validDateMax = parseLocalDate(jDatafield, "valid_date_max"),
            validStringLengthMax = parseLong(jDatafield, "valid_string_length_max"),
            validStringLengthMin = parseLong(jDatafield, "valid_string_length_min"),
            validFilePortrait = jDatafield.getBoolean("valid_file_portrait"),
            type = parseType(jDatafield),
            internalName = jDatafield.getString("internal_name"),
            options = parseOptions(jDatafield),
        )
        val row = QuestionnaireModel.RowSpec(
            dataField = datafield,
            required = jRow.getBoolean("required"),
            label = I18nString.toString(jRow.getJSONObject("label")) ?: "",
            helpText = I18nString.toString(jRow.getJSONObject("help_text")) ?: "",
            renderedHelpText = jRow.getString("rendered_help_text"),
            dependencyDataField = if (jRow.isNull("dependency_datafield")) null else jRow.getString("dependency_datafield"),
            dependencyValues = parseStringList(jRow.getJSONArray("dependency_values")),
        )
        rows.add(row)
    }

    return QuestionnaireModel(
        id = this.server_id!!,
        type = this.type!!,
        position = this.position!!,
        internalName = json.getString("internal_name"),
        allSalesChannels = json.getBoolean("all_sales_channels"),
        limitSalesChannels = parseStringList(json.getJSONArray("limit_sales_channels")),
        rows = rows,
    )
}


private fun parseBigDecimal(obj: JSONObject, key: String): BigDecimal? {
    try {
        if (obj.isNull(key)) return null
        return BigDecimal(obj.getString(key))
    } catch (e: JSONException) {
        return null
    } catch (e: NumberFormatException) {
        return null
    }
}


private fun parseLong(obj: JSONObject, key: String): Long? {
    try {
        if (obj.isNull(key)) return null
        return obj.getString(key).toLong()
    } catch (e: JSONException) {
        return null
    } catch (e: NumberFormatException) {
        return null
    }
}


private fun parseLocalDate(obj: JSONObject, key: String): LocalDate? {
    try {
        if (obj.isNull(key)) return null
        return LocalDate.parse(obj.getString(key))
    } catch (e: JSONException) {
        return null
    } catch (e: DateTimeParseException) {
        return null
    }
}

private fun parseType(json: JSONObject): QuestionType {
    return try {
        QuestionType.valueOf(json.getString("type"))
    } catch (e: JSONException) {
        QuestionType.T
    } catch (e: IllegalArgumentException) {
        QuestionType.T
    }
}

private fun parseOptions(json: JSONObject): List<QuestionOption> {
    val jOptions = json.getJSONArray("options")
    val response = mutableListOf<QuestionOption>()
    for (i in 0 until jOptions.length()) {
        val jOpt = jOptions.getJSONObject(i)
        response.add(
            QuestionOption(
                serverId = null,
                identifier = jOpt.getString("identifier"),
                value = I18nString.toString(jOpt.getJSONObject("answer")) ?: "",
                position = jOpt.getLong("position"),
            )
        )
    }
    return response.toImmutableList()
}

private fun parseStringList(json: JSONArray): List<String> {
    val response = mutableListOf<String>()
    for (i in 0 until json.length()) {
        response.add(json.getString(i))
    }
    return response.toImmutableList()
}


@Deprecated("serverId should not be used, can be 0 for system fields, remove when old proxy support is fully removed")
fun QuestionnaireModel.Row.toLegacyLikeJSON(): String {
    val datetimef = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    val datef = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    val jo = JSONObject()
    jo.put("id", dataField!!.serverId)
    jo.put("question", JSONObject().apply { put("en", label) })
    jo.put("help_text", JSONObject().apply { put("en", helpText) })
    jo.put("type", type.name)
    jo.put("required", required)
    jo.put("items", JSONArray())  // ignored for this purpose
    jo.put("position", 1)  // ignored for this purpose
    jo.put("identifier", identifier)
    jo.put("ask_during_checkin", parent.type == "position_checkin")
    jo.put("show_during_checkin", dataField.showDuringCheckin)
    jo.put("hidden", parent.type == "position_hidden")
    jo.put("print_on_invoice", dataField.printOnInvoice)
    jo.put("valid_number_min", dataField.validNumberMin)
    jo.put("valid_number_max", dataField.validNumberMin)
    jo.put("valid_date_min", dataField.validDateMin?.format(datef))
    jo.put("valid_date_max", dataField.validDateMax?.format(datef))
    jo.put("valid_datetime_min", dataField.validDatetimeMin?.format(datetimef))
    jo.put("valid_datetime_max", dataField.validDatetimeMax?.format(datetimef))
    jo.put("valid_string_length_min", dataField.validStringLengthMin)
    jo.put("valid_string_length_max", dataField.validStringLengthMax)
    jo.put("valid_file_portrait", dataField.validFilePortrait)
    // dependency_question not supported

    val options = JSONArray()
    for (opt in this.options ?: emptyList()) {
        val jopt = JSONObject()
        jopt.put("id", opt.serverId ?: 0L)
        jopt.put("identifier", opt.identifier)
        jopt.put("position", opt.position)
        jopt.put("answer", JSONObject().apply { put("en", opt.value) })
        options.put(jopt)
    }

    jo.put("options", options)
    return jo.toString()
}
