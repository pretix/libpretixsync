package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    public boolean hasVariations() {
        try {
            return getJSON().getBoolean("has_variations");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<ItemVariation> getActiveVariations() {
        List<ItemVariation> l = new ArrayList<>();
        try {
            JSONArray vars = getJSON().getJSONArray("variations");
            for (int i = 0; i < vars.length(); i++) {
                JSONObject var = vars.getJSONObject(i);
                ItemVariation v = new ItemVariation();
                v.setActive(var.getBoolean("active"));
                v.setDescription(var.optJSONObject("description"));
                v.setPosition(var.getLong("position"));
                v.setPrice(new BigDecimal(var.getString("price")));
                v.setServer_id(var.getLong("id"));
                v.setValue(var.getJSONObject("value"));
                if (v.isActive()) {
                    l.add(v);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return l;
    }

    public BigDecimal getDefaultPrice() {
        try {
            return new BigDecimal(getJSON().getString("default_price"));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BigDecimal(0.00);
        }
    }
}
