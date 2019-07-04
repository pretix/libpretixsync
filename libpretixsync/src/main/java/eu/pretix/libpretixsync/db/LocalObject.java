package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

public interface LocalObject {
    public JSONObject toJSON() throws JSONException;
}
