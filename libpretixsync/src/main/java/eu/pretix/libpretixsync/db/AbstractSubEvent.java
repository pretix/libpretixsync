package eu.pretix.libpretixsync.db;

import io.requery.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Date;

import eu.pretix.libpretixsync.utils.I18nString;

@Entity(cacheable = false)
public class AbstractSubEvent implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String event_slug;

    public Date date_from;

    @Nullable
    public Date date_to;

    public boolean active;

    @Column(definition = "TEXT")
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

    public BigDecimal getPriceForItem(Long item_id, BigDecimal original_price) throws JSONException {
        JSONObject jd = getJSON();
        JSONArray ja = jd.getJSONArray("item_price_overrides");
        for (int i = 0; i < ja.length(); i++) {
            JSONObject or = ja.getJSONObject(i);
            if (or.getLong("item") == item_id) {
                if (or.isNull("price")) {
                    return original_price;
                }
                return new BigDecimal(or.getString("price"));
            }
        }
        return original_price;
    }

    public BigDecimal getPriceForVariation(Long var_id, BigDecimal original_price) throws JSONException {
        JSONObject jd = getJSON();
        JSONArray ja = jd.getJSONArray("variation_price_overrides");
        for (int i = 0; i < ja.length(); i++) {
            JSONObject or = ja.getJSONObject(i);
            if (or.getLong("variation") == var_id) {
                if (or.isNull("price")) {
                    return original_price;
                }
                return new BigDecimal(or.getString("price"));
            }
        }
        return original_price;
    }
}
