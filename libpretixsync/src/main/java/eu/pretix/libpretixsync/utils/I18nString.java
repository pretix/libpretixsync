package eu.pretix.libpretixsync.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

public class I18nString {
    public static String toString(JSONObject str) {
        String lng = Locale.getDefault().getLanguage();
        String[] lngparts = lng.split("[-_]");
        try {
            if (str.has(lng) && !str.getString(lng).equals("")) {
                return str.getString(lng);
            } else {
                for (Iterator it = str.keys(); it.hasNext(); ) {
                    String key = (String) it.next();
                    String[] parts = key.split("[-_]");
                    if (parts[0].equals(lngparts[0]) && !str.getString(key).equals("")) {
                        return str.getString(key);
                    }
                }
                if (str.has("en") && !str.getString("en").equals("")) {
                    return str.getString("en");
                } else if (str.length() > 0) {
                    return str.getString((String) str.keys().next());
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
