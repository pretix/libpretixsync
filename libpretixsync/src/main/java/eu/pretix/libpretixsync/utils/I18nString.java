package eu.pretix.libpretixsync.utils;

import org.json.JSONException;
import org.json.JSONObject;

public class I18nString {
    public static String toString(JSONObject str) {
        try {
            if (str.has("en")) {
                return str.getString("en");
            } else if (str.length() > 0) {
                return str.getString((String) str.keys().next());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
