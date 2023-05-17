package eu.pretix.libpretixsync.db;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import io.requery.Column;
import io.requery.Entity;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;
import io.requery.Nullable;

@Entity(cacheable = false)
public class AbstractMediumKeySet implements RemoteObject {

    @Generated
    @Key
    public Long id;

    @Nullable
    @Index
    public Long public_id;

    public String media_type;

    public String organizer;

    public boolean active;

    public String uid_key;

    public String diversification_key;

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
