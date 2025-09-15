package eu.pretix.libpretixsync.models.db

import eu.pretix.libpretixsync.models.Question as QuestionModel
import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.db.QuestionOption
import eu.pretix.libpretixsync.sqldelight.Question
import eu.pretix.libpretixsync.utils.I18nString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun Question.toModel(): QuestionModel {
    val json = JSONObject(this.json_data!!)

    return QuestionModel(
        id = this.id,
        serverId = this.server_id!!,
        eventSlug = this.event_slug,
        position = this.position!!,
        required = this.required,
        askDuringCheckIn = parseAskDuringCheckIn(json),
        showDuringCheckIn = parseShowDuringCheckIn(json),
        dependencyQuestionServerId = parseDependencyQuestionId(json),
        dependencyValues = parseDependencyValues(json),
        type = parseType(json),
        identifier = parseIdentifier(json),
        question = parseQuestion(json),
        options = parseOptions(json),
    )
}

private fun parseAskDuringCheckIn(json: JSONObject): Boolean {
    return try {
        json.getBoolean("ask_during_checkin")
    } catch (e: JSONException) {
        e.printStackTrace()
        false
    }
}

private fun parseShowDuringCheckIn(json: JSONObject): Boolean {
    return try {
        json.getBoolean("show_during_checkin")
    } catch (e: JSONException) {
        e.printStackTrace()
        false
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

private fun parseIdentifier(json: JSONObject): String {
    return try {
        json.getString("identifier")
    } catch (e: JSONException) {
        e.printStackTrace()
        "<invalid>"
    }
}

private fun parseQuestion(json: JSONObject): String {
    return try {
        I18nString.toString(json.getJSONObject("question"))
    } catch (e: JSONException) {
        e.printStackTrace()
        "<invalid>"
    }
}

private fun parseOptions(json: JSONObject): List<QuestionOption>? {
    val opts: MutableList<QuestionOption> = ArrayList()
    return try {
        val arr: JSONArray = json.getJSONArray("options")
        for (i in 0 until arr.length()) {
            val oobj = arr.getJSONObject(i)
            var answ: String?
            answ = try {
                I18nString.toString(oobj.getJSONObject("answer"))
            } catch (e: JSONException) {
                oobj.getString("answer")
            }
            opts.add(
                QuestionOption(
                    oobj.getLong("id"),
                    oobj.getLong("position"),
                    oobj.getString("identifier"),
                    answ
                )
            )
        }
        opts
    } catch (e: JSONException) {
        e.printStackTrace()
        null
    }
}

private fun parseDependencyQuestionId(json: JSONObject): Long? {
    return try {
        // Use getLong instead of optLong like in AbstractQuestion
        // We want an explicit null here
        if (json.isNull("dependency_question")) {
            null
        } else {
            json.getLong("dependency_question")
        }
    } catch (e: JSONException) {
        e.printStackTrace()
        null
    }
}

private fun parseDependencyValues(json: JSONObject): List<String> {
    try {
        val l: MutableList<String> = java.util.ArrayList()
        val a = json.optJSONArray("dependency_values")
        if (a != null) {
            for (i in 0 until a.length()) {
                l.add(a.getString(i))
            }
        }
        return l
    } catch (e: JSONException) {
        e.printStackTrace()
        return java.util.ArrayList()
    }
}
