package eu.pretix.libpretixsync.db;

import io.requery.*;

import org.json.JSONArray;
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

    public Long deleteAfterTimestamp;

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

    public BigDecimal getPendingTotal() {
        try {
            JSONObject j = getJSON();

            BigDecimal total = new BigDecimal(j.getString("total"));
            if (j.getString("status").equals("c")) {
                total = BigDecimal.ZERO;
            }

            BigDecimal paymentSum = BigDecimal.ZERO;
            JSONArray payments = j.getJSONArray("payments");
            for (int i = 0; i < payments.length(); i++) {
                JSONObject payment = payments.getJSONObject(i);
                if (payment.getString("state").matches("^(confirmed|refunded)$")) {
                    paymentSum = paymentSum.add(new BigDecimal(payment.getString("amount")));
                }
            }

            BigDecimal refundSum = BigDecimal.ZERO;
            JSONArray refunds = j.getJSONArray("refunds");
            for (int i = 0; i < refunds.length(); i++) {
                JSONObject refund = refunds.getJSONObject(i);
                if (refund.getString("state").matches("^(done|transit|created)$")) {
                    refundSum = refundSum.add(new BigDecimal(refund.getString("amount")));
                }
            }
            return total.subtract(paymentSum).add(refundSum);
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
