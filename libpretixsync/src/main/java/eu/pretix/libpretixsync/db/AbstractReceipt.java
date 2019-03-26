package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToOne;
import io.requery.Nullable;
import io.requery.OneToMany;

@Entity(cacheable = false)
public class AbstractReceipt implements LocalObject {
    @Generated
    @Key
    public Long id;

    public String event_slug;

    @Column(nullable = false, value = "\"EUR\"")
    public String currency;

    public String order_code;

    @Nullable
    public Long server_id;

    public Boolean open;

    @Nullable
    public Date datetime_opened;

    @Nullable
    public Date datetime_closed;

    public String payment_type;

    @OneToMany
    public List<ReceiptLine> lines;

    @ForeignKey
    @ManyToOne
    public Closing closing;

    public Boolean canceled;

    public String payment_data;

    @Column(nullable = false)
    public Boolean printed;

    @Override
    public JSONObject toJSON() throws JSONException {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);

        JSONObject jo = new JSONObject();
        jo.put("receipt_id", id);
        jo.put("event", event_slug != null ? event_slug : JSONObject.NULL);
        jo.put("order", order_code != null ? order_code : JSONObject.NULL);
        jo.put("order_full", order_code != null ? event_slug.toUpperCase() + "-" + order_code : "-");
        jo.put("open", open);
        jo.put("payment_type", payment_type);
        jo.put("datetime_opened", df.format(datetime_opened));
        jo.put("datetime_closed", df.format(datetime_closed));
        jo.put("closing_id", closing.getId());
        jo.put("canceled", canceled);
        jo.put("currency", currency);
        jo.put("printed", printed);
        jo.put("payment_data", payment_data == null || payment_data.equals("null") || payment_data.isEmpty() ? new JSONObject() : new JSONObject(payment_data));
        return jo;
    }
}
