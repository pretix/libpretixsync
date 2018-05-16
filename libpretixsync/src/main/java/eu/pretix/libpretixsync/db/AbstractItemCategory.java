package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public class AbstractItemCategory implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public String event_slug;

    public Long server_id;

    public Long position;

    public boolean is_addon;

    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    @Override
    public void fromJSON(JSONObject data) throws JSONException {
        server_id = data.getLong("id");
        position = data.optLong("position", 0L);
        is_addon = data.optBoolean("is_addon", false);
        json_data = data.toString();
    }
}
