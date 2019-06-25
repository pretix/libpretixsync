package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.pretix.libpretixsync.utils.I18nString;
import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToMany;
import io.requery.Nullable;

@Entity(cacheable = false)
public class AbstractEvent implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public String slug;

    public String currency;

    public Date date_from;

    @Nullable
    public Date date_to;

    public boolean live;

    public boolean has_subevents;

    public String json_data;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    public boolean hasSeating() {
        try {
            return !getJSON().isNull("seating_plan");
        } catch (JSONException e) {
            return false;
        }
    }

    public String getName() {
        try {
            return I18nString.toString(getJSON().getJSONObject("name"));
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }
}
