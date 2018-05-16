package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.JunctionTable;
import io.requery.Key;
import io.requery.ManyToMany;
import io.requery.Table;

@Table(name = "orders")
@Entity(cacheable = false)
public class AbstractOrder implements RemoteObject {

    @Generated
    @Key
    public Long id;

    public String event_slug;

    public String code;

    public String status;

    public String email;

    public boolean checkin_attention;

    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    @Override
    public void fromJSON(JSONObject data) throws JSONException {
        code = data.getString("code");
        status = data.getString("status");
        email = data.getString("email");
        checkin_attention = data.optBoolean("checkin_attention", false);
        json_data = data.toString();
    }

}
