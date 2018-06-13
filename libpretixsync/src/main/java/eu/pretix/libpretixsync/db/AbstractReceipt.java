package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import eu.pretix.libpretixsync.utils.I18nString;
import io.requery.Column;
import io.requery.Entity;
import io.requery.ForeignKey;
import io.requery.Generated;
import io.requery.Key;
import io.requery.ManyToMany;
import io.requery.ManyToOne;
import io.requery.Naming;
import io.requery.Nullable;
import io.requery.OneToMany;

@Entity(cacheable = false)
public class AbstractReceipt implements LocalObject {
    @Generated
    @Key
    public Long id;

    public String event_slug;

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

    @Override
    public JSONObject toJSON() throws JSONException {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);

        JSONObject jo = new JSONObject();
        jo.put("receipt_id", id);
        jo.put("event", event_slug != null ? event_slug : JSONObject.NULL);
        jo.put("order", order_code != null ? order_code : JSONObject.NULL);
        jo.put("open", open);
        jo.put("payment_type", payment_type);
        jo.put("datetime_opened", df.format(datetime_opened));
        jo.put("datetime_closed", df.format(datetime_closed));
        jo.put("closing_id", closing.getId());
        jo.put("canceled", canceled);
        return jo;
    }
}
