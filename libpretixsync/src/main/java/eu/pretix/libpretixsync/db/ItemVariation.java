package eu.pretix.libpretixsync.db;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.Charset;

import eu.pretix.libpretixsync.utils.I18nString;

public class ItemVariation implements Serializable {
    private Long server_id;
    private BigDecimal listed_price;
    private BigDecimal price;
    private boolean active;
    private JSONObject description;
    private JSONObject value;
    private Long position;
    private Long available;
    private Long available_number;
    private JSONArray sales_channels;
    private String available_from;
    private String available_until;
    private boolean hide_without_voucher;

    public void setServer_id(Long server_id) {
        this.server_id = server_id;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setListed_price(BigDecimal listed_price) {
        this.listed_price = listed_price;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDescription(JSONObject description) {
        this.description = description;
    }

    public void setValue(JSONObject value) {
        this.value = value;
    }

    public void setPosition(Long position) {
        this.position = position;
    }

    public Long getServer_id() {
        return server_id;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getListedPrice() { return listed_price; }

    public boolean isActive() {
        return active;
    }

    public JSONObject getDescription() {
        return description;
    }

    public JSONObject getValue() {
        return value;
    }

    public String getStringValue() {
        return I18nString.toString(getValue());
    }

    public Long getPosition() {
        return position;
    }

    public Long getAvailable() {
        return available;
    }

    public void setAvailable(Long available) {
        this.available = available;
    }

    public Long getAvailable_number() {
        return available_number;
    }

    public void setAvailable_number(Long available_number) {
        this.available_number = available_number;
    }

    public JSONArray getSales_channels() {
        return sales_channels;
    }

    public void setSales_channels(JSONArray sales_channels) {
        this.sales_channels = sales_channels;
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

    public boolean isHide_without_voucher() {
        return hide_without_voucher;
    }

    public void setHide_without_voucher(boolean hide_without_voucher) {
        this.hide_without_voucher = hide_without_voucher;
    }

    private void writeObject(java.io.ObjectOutputStream out)
            throws IOException {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("server_id", server_id);
            jsonObject.put("price", price.toPlainString());
            jsonObject.put("active", active);
            jsonObject.put("description", description);
            jsonObject.put("value", value);
            jsonObject.put("position", position);
            jsonObject.put("available", available);
            jsonObject.put("available_number", available_number);
            jsonObject.put("sales_channels", sales_channels);
            jsonObject.put("available_from", available_from);
            jsonObject.put("available_until", available_until);
            jsonObject.put("hide_without_voucher", hide_without_voucher);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        out.writeObject(jsonObject.toString());
        out.close();
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        String json = (String) in.readObject();
        in.close();
        try {
            JSONObject jsonObject = new JSONObject(json);

            server_id = jsonObject.getLong("server_id");
            price = new BigDecimal(jsonObject.getString("price"));
            active = jsonObject.getBoolean("active");
            description = jsonObject.getJSONObject("description");
            value = jsonObject.getJSONObject("value");
            position = jsonObject.getLong("position");
            available = !jsonObject.isNull("available") ? jsonObject.optLong("available") : null;
            available_number = !jsonObject.isNull("available_number") ? jsonObject.optLong("available_number") : null;
            available_from = jsonObject.optString("available_from");
            available_until = jsonObject.optString("available_until");
            sales_channels = jsonObject.optJSONArray("sales_channels");
            hide_without_voucher = jsonObject.getBoolean("hide_without_voucher");
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void readObjectNoData()
            throws ObjectStreamException {
    }

    public boolean availableOnSalesChannel(String channel) {
        try {
            if (sales_channels == null) {
                return true;
            }
            for (int i = 0; i < sales_channels.length(); i++) {
                if (sales_channels.getString(i).equals(channel)) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
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
