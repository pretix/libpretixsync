package eu.pretix.libpretixsync.db;

import io.requery.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.List;

@Table(name = "orders")
@Entity(cacheable = false)
public class AbstractOrder implements RemoteObject {

    @Generated
    @Key
    public Long id;

    public String event_slug;

    @Index
    public String code;

    public String status;

    public String email;

    public boolean checkin_attention;

    @Column(definition = "TEXT")
    public String json_data;

    @OneToMany
    public List<OrderPosition> positions;

    public String getPaymentProvider() {
        try {
            return getJSON().getString("payment_provider");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigDecimal getTotal() {
        try {
            return new BigDecimal(getJSON().getString("total"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

}
