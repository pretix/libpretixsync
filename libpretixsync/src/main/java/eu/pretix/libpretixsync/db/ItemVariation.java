package eu.pretix.libpretixsync.db;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
    private BigDecimal price;
    private boolean active;
    private JSONObject description;
    private JSONObject value;
    private Long position;
    private Long available;
    private Long available_number;

    public void setServer_id(Long server_id) {
        this.server_id = server_id;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void readObjectNoData()
            throws ObjectStreamException {
    }
}
