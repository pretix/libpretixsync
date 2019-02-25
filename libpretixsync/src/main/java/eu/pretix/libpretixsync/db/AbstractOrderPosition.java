package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;

import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Nullable;
import io.requery.ReferentialAction;

@Entity(cacheable = false)
public class AbstractOrderPosition implements RemoteObject {

    @Generated
    @Key
    public Long id;

    public Long server_id;

    @Column(name="order_ref")
    @ForeignKey(update = ReferentialAction.CASCADE)
    @ManyToOne
    public Order order;

    public Long positionid;

    @Nullable
    public String attendee_name;

    @Nullable
    public String attendee_email;

    @ForeignKey(update = ReferentialAction.SET_NULL)
    @ManyToOne
    public Item item;

    public String secret;

    public String json_data;

    public BigDecimal getPrice() {
        try {
            return new BigDecimal(getJSON().getString("price"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigDecimal getTaxRate() {
        try {
            return new BigDecimal(getJSON().getString("tax_rate"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigDecimal getTaxValue() {
        try {
            return new BigDecimal(getJSON().getString("tax_value"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getTaxRule() {
        try {
            long var = getJSON().optLong("tax_rule", 0L);
            if (var == 0) {
                return null;
            }
            return var;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getSubeventId() {
        try {
            long var = getJSON().optLong("subevent", 0L);
            if (var == 0) {
                return null;
            }
            return var;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getVariationId() {
        try {
            long var = getJSON().optLong("variation", 0L);
            if (var == 0) {
                return null;
            }
            return var;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    public void fromJSON(JSONObject data) throws JSONException {
        server_id = data.getLong("server_id");
        positionid = data.getLong("position");
        attendee_name = data.getString("attendee_name");
        attendee_email = data.getString("attendee_email");
        secret = data.getString("secret");
        json_data = data.toString();
    }

}
