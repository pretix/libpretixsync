package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.List;

import eu.pretix.libpretixsync.utils.I18nString;
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

    public String getName() {
        try {
            return I18nString.toString(getJSON().getJSONObject("name"));
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public BigDecimal getDefaultPrice() {
        try {
            return new BigDecimal(getJSON().getString("default_price"));
        } catch (JSONException e) {
            return new BigDecimal(0.00);
        }
    }
}
