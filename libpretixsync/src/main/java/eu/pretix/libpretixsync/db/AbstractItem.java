package eu.pretix.libpretixsync.db;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
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

    public String picture_filename;

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

    public boolean isGenerateTickets() {
        try {
            if (getJSON().isNull("generate_tickets")) {
                return true;
            }
            return getJSON().getBoolean("generate_tickets");
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
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

    public boolean availableByTime() {
        try {
            JSONObject jo = getJSON();
            if (!jo.isNull("available_from")) {
                DateTime af = ISODateTimeFormat.dateTimeParser().parseDateTime(jo.getString("available_from"));
                if (af.isAfterNow()) {
                    return false;
                }
            }
            if (!jo.isNull("available_until")) {
                DateTime af = ISODateTimeFormat.dateTimeParser().parseDateTime(jo.getString("available_until"));
                if (af.isBeforeNow()) {
                    return false;
                }
            }
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    public boolean availableOnSalesChannel(String channel) {
        try {
            JSONArray channels = getJSON().optJSONArray("sales_channels");
            if (channels == null) {
                return true;
            }
            for (int i = 0; i < channels.length(); i++) {
                if (channels.getString(i).equals(channel)) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    public List<ItemVariation> getVariations() throws JSONException {
        List<ItemVariation> l = new ArrayList<>();
        JSONArray vars = getJSON().getJSONArray("variations");
        for (int i = 0; i < vars.length(); i++) {
            JSONObject variation = vars.getJSONObject(i);
            ItemVariation v = new ItemVariation();
            v.setActive(variation.getBoolean("active"));
            v.setDescription(variation.optJSONObject("description"));
            v.setPosition(variation.getLong("position"));
            v.setPrice(new BigDecimal(variation.getString("price")));
            v.setServer_id(variation.getLong("id"));
            v.setValue(variation.getJSONObject("value"));
            l.add(v);
        }
        return l;
    }

    public ItemVariation getVariation(Long variation_id) throws JSONException {
        for (ItemVariation var : getVariations()) {
            if (var.getServer_id().equals(variation_id)) {
                return var;
            }
        }
        return null;
    }
}
