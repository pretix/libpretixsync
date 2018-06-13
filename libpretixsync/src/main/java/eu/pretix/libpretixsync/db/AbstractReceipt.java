package eu.pretix.libpretixsync.db;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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
        JSONObject jo = new JSONObject();
        jo.put("id", id);
        jo.put("server_id", server_id);
        jo.put("event_slug", event_slug);
        jo.put("open", open);
        jo.put("payment_type", payment_type);
        jo.put("datetime_opened", datetime_opened);
        jo.put("datetime_closed", datetime_closed);
        jo.put("order_code", order_code);
        jo.put("closing", closing.getId());
        jo.put("canceled", order_code);

        JSONArray linesarr = new JSONArray();
        for (ReceiptLine line : lines) {
            linesarr.put(line.toJSON());
        }
        jo.put("lines", linesarr);
        return jo;
    }
}
