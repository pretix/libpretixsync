package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public class AbstractTaxRule implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    @Override
    public void fromJSON(JSONObject data) throws JSONException {
        server_id = data.getLong("id");
        json_data = data.toString();
    }
}
