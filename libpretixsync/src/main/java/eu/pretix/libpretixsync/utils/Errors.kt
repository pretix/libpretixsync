package eu.pretix.libpretixsync.utils

import org.json.JSONArray
import org.json.JSONObject


fun flatJsonErrorList(err: JSONArray): List<String> {
    val parts = emptyList<String>().toMutableList()

    for (key in 0..(err.length() - 1)) {
        val e = err.get(key)
        if (e is JSONObject) {
            parts.addAll(flatJsonErrorList(e))
        } else if (e is JSONArray) {
            parts.addAll(flatJsonErrorList(e))
        } else if (e is String) {
            parts.add(e)
        }
    }
    return parts
}


fun flatJsonErrorList(err: JSONObject): List<String> {
    val parts = emptyList<String>().toMutableList()

    for (key in err.keys()) {
        val e = err.get(key as String)
        if (e is JSONObject) {
            parts.addAll(flatJsonErrorList(e))
        } else if (e is JSONArray) {
            parts.addAll(flatJsonErrorList(e))
        } else if (e is String) {
            parts.add(e)
        }
    }
    return parts
}

fun flatJsonError(err: JSONObject): String {
    return flatJsonErrorList(err).joinToString("\n")
}
