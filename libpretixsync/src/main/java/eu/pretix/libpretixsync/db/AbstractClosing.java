package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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

    public Date datetime;

    public boolean open;

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

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("id", id);
        jo.put("server_id", server_id);
        jo.put("first_receipt", first_receipt);
        jo.put("last_receipt", last_receipt);
        jo.put("payment_sum", payment_sum);
        jo.put("payment_sum_cash", payment_sum_cash);
        jo.put("datetime", datetime);
        return jo;
    }
}
