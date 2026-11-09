package eu.pretix.libpretixsync.utils

import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

object I18nString {
    @JvmStatic
    fun toString(str: JSONObject): String? {
        val lng = Locale.getDefault().getLanguage()
        val lngparts: Array<String?> =
            lng.split("[-_]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        try {
            if (str.has(lng) && str.getString(lng) != "") {
                return str.getString(lng)
            } else {
                val it = str.keys()
                while (it.hasNext()) {
                    val key = it.next() as String
                    val parts: Array<String?> =
                        key.split("[-_]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (parts[0] == lngparts[0] && str.getString(key) != "") {
                        return str.getString(key)
                    }
                }
                if (str.has("en") && str.getString("en") != "") {
                    return str.getString("en")
                } else if (str.length() > 0) {
                    return str.getString(str.keys().next() as String?)
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }
}
