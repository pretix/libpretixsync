package eu.pretix.libpretixsync.db;

import io.requery.*;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Date;

import eu.pretix.libpretixsync.utils.I18nString;

@Entity(cacheable = false)
public class AbstractSubEvent implements RemoteObject {

    public class ItemOverride {
        private String available_from;
        private String available_until;
        private BigDecimal price;
        private boolean disabled;

        public ItemOverride(String available_from, String available_until, BigDecimal price, boolean disabled) {
            this.available_from = available_from;
            this.available_until = available_until;
            this.price = price;
            this.disabled = disabled;
        }

        public String getAvailable_from() {
            return available_from;
        }

        public void setAvailable_from(String available_from) {
            this.available_from = available_from;
        }

        public String getAvailable_until() {
            return available_until;
        }

        public void setAvailable_until(String available_until) {
            this.available_until = available_until;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public boolean isDisabled() {
            return disabled;
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        public boolean availableByTime() {
            if (available_from != null && available_from.length() > 5) {
                DateTime af = ISODateTimeFormat.dateTimeParser().parseDateTime(available_from);
                if (af.isAfterNow()) {
                    return false;
                }
            }
            if (available_until != null && available_until.length() > 5) {
                DateTime af = ISODateTimeFormat.dateTimeParser().parseDateTime(available_until);
                if (af.isBeforeNow()) {
                    return false;
                }
            }
            return true;
        }
    }

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

    public ItemOverride getOverrideForItem(Long item_id) throws JSONException {
        JSONObject jd = getJSON();
        JSONArray ja = jd.getJSONArray("item_price_overrides");
        for (int i = 0; i < ja.length(); i++) {
            JSONObject or = ja.getJSONObject(i);
            if (or.getLong("item") == item_id) {
                return new ItemOverride(
                        or.optString("available_from"),
                        or.optString("available_until"),
                        or.isNull("price") ? null : new BigDecimal(or.optString("price")),
                        or.optBoolean("disabled", false)
                );
            }
        }
        return new ItemOverride(null, null, null, false);
    }

    public ItemOverride getOverrideForVariation(Long var_id) throws JSONException {
        JSONObject jd = getJSON();
        JSONArray ja = jd.getJSONArray("variation_price_overrides");
        for (int i = 0; i < ja.length(); i++) {
            JSONObject or = ja.getJSONObject(i);
            if (or.getLong("variation") == var_id) {
                return new ItemOverride(
                        or.optString("available_from"),
                        or.optString("available_until"),
                        or.isNull("price") ? null : new BigDecimal(or.optString("price")),
                        or.optBoolean("disabled", false)
                );
            }
        }
        return new ItemOverride(null, null, null, false);
    }
}
