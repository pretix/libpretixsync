package eu.pretix.libpretixsync.db;

import io.requery.Column;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public class AbstractTicketLayout implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String event_slug;

    public String background_filename;

    public boolean is_default;

    @Column(definition = "TEXT")
    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
