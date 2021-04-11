package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import eu.pretix.libpretixsync.BuildConfig;
import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Nullable;
import io.requery.OneToMany;

@Entity(cacheable = false)
public class AbstractReceiptPayment implements LocalObject {
    @Generated
    @Key
    public Long id;

    @ForeignKey
    @ManyToOne
    public Receipt receipt;

    public String payment_type;

    public String status;

    public BigDecimal amount;

    @Nullable
    public String detailsJson;

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("id", id);
        jo.put("payment_type", payment_type);
        jo.put("status", status);
        jo.put("amount", amount);
        jo.put("payment_data", (detailsJson == null || detailsJson.equals("null")) ? new JSONObject() : new JSONObject(detailsJson));
        return jo;
    }
}
