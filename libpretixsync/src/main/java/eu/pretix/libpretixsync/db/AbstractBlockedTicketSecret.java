package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import io.requery.Column;
import io.requery.Entity;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;

@Entity(cacheable = false)
public abstract class AbstractBlockedTicketSecret implements RemoteObject {

    @Key
    @Generated
    public Long id;

    public Long server_id;

    @Index
    public String secret;

    public String updated;

    public boolean blocked;

    @Index
    public String event_slug;

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
