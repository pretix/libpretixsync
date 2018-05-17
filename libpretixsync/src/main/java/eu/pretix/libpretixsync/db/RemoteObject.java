package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

public interface RemoteObject {
    public JSONObject getJSON() throws JSONException;
}
