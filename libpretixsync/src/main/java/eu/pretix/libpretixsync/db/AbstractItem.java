package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    List<Quota> quotas;

    @Nullable
    public Long badge_layout_id;

    @Nullable
    public Long ticket_layout_id;

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

    public BigDecimal getDefaultPrice() {
        try {
            return new BigDecimal(getJSON().getString("default_price"));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BigDecimal(0.00);
        }
    }

    public Long getTaxRuleId() {
        try {
            return getJSON().optLong("tax_rule");
        } catch (JSONException e) {
            e.printStackTrace();
            return Long.valueOf(0);
        }
    }
}
