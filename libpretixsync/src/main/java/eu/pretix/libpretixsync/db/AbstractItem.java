package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToMany;
import io.requery.Nullable;

@Entity(cacheable = false)
public class AbstractItem implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public String event_slug;

    public Long server_id;

    public Long position;

    @Nullable
    public Long category_id;

    public boolean admission;

    public boolean active;

    public String json_data;

    @ManyToMany
    List<Question> questions;

    @ManyToMany
    List<Question> quotas;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    @Override
    public void fromJSON(JSONObject data) throws JSONException {
        server_id = data.getLong("id");
        position = data.optLong("position", 0L);
        category_id = data.optLong("category");
        admission = data.optBoolean("admission", false);
        active = data.optBoolean("active", true);
        json_data = data.toString();
    }
}
