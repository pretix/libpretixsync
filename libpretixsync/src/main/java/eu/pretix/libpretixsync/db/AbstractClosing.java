package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;
import io.requery.Nullable;
import io.requery.OneToMany;

@Entity(cacheable = false)
public class AbstractClosing implements LocalObject {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    @Nullable
    public boolean dsfinvk_uploaded;

    public Date datetime;

    public boolean open;

    public Long cashier_numericid;

    public String cashier_userid;

    public String cashier_name;

    @Nullable
    public Long first_receipt;

    @Nullable
    public Long last_receipt;

    @OneToMany
    public List<Receipt> receipts;

    @Nullable
    public BigDecimal payment_sum;

    @Nullable
    public BigDecimal payment_sum_cash;

    @Nullable
    public BigDecimal cash_counted;

    @Nullable
    public String json_data;

    @Nullable
    public String invoice_settings;

    public JSONObject getInvoiceSettings() throws JSONException {
        try {
            return new JSONObject(invoice_settings != null ? invoice_settings : "{}");
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);

        JSONObject jo = new JSONObject();
        jo.put("closing_id", id);
        jo.put("first_receipt", first_receipt);
        jo.put("last_receipt", last_receipt);
        jo.put("payment_sum", payment_sum.setScale(2, RoundingMode.HALF_UP));
        jo.put("payment_sum_cash", payment_sum_cash.setScale(2, RoundingMode.HALF_UP));
        jo.put("cash_counted", cash_counted.setScale(2, RoundingMode.HALF_UP));
        jo.put("datetime", df.format(datetime));
        jo.put("invoice_settings", invoice_settings);
        jo.put("cashier", cashier_numericid);
        jo.put("data", json_data != null ? new JSONObject(json_data) : new JSONObject());
        return jo;
    }
}
