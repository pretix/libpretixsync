package eu.pretix.libpretixsync.db;

import eu.pretix.libpretixsync.BuildConfig;
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
import io.requery.Index;
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

    @Column(nullable = false, value = "'EUR'")
    public String currency;

    public String order_code;

    @Nullable
    public String chosen_cart_id;

    @Nullable
    public Long server_id;

    @Column(value = BuildConfig.BOOLEAN_FALSE)
    public boolean open;

    @Nullable
    public Date datetime_opened;

    @Nullable
    public Date datetime_closed;

    public String payment_type;

    public String fiscalisation_data;

    public String fiscalisation_text;

    public String fiscalisation_qr;

    @Nullable
    public String additional_text;

    @Column(nullable = true)
    public String email_to;

    @OneToMany
    public List<ReceiptLine> lines;

    @OneToMany
    public List<ReceiptPayment> payments;

    @ForeignKey
    @ManyToOne
    @Index
    public Closing closing;

    public Long cashier_numericid;

    public String cashier_userid;

    public String cashier_name;

    public boolean canceled;

    @Column(value = BuildConfig.BOOLEAN_FALSE)
    public boolean started;

    public String payment_data;

    @Column(nullable = false, value = BuildConfig.BOOLEAN_FALSE)
    public boolean printed;

    @Column(nullable = false, value = BuildConfig.BOOLEAN_FALSE)
    public boolean training;

    @OneToMany
    public List<QueuedOrder> queuedorders;

    @Override
    public JSONObject toJSON() throws JSONException {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);

        JSONObject jo = new JSONObject();
        jo.put("receipt_id", id);
        jo.put("event", event_slug != null ? event_slug : JSONObject.NULL);
        jo.put("order", order_code != null ? order_code : JSONObject.NULL);
        jo.put("order_full", order_code != null ? event_slug.toUpperCase() + "-" + order_code : "-");
        jo.put("open", open);
        jo.put("payment_type", payment_type);
        jo.put("datetime_opened", datetime_opened != null ? df.format(datetime_opened) : JSONObject.NULL);
        jo.put("datetime_closed", datetime_closed != null ? df.format(datetime_closed) : JSONObject.NULL);
        jo.put("closing_id", closing.getId());
        jo.put("canceled", canceled);
        jo.put("currency", currency);
        jo.put("printed", printed);
        jo.put("email_to", email_to);
        jo.put("payment_data", payment_data == null || payment_data.equals("null") || payment_data.isEmpty() ? new JSONObject() : new JSONObject(payment_data));
        jo.put("fiscalisation_data", fiscalisation_data == null || fiscalisation_data.equals("null") || fiscalisation_data.isEmpty() ? new JSONObject() : new JSONObject(fiscalisation_data));
        jo.put("fiscalisation_text", fiscalisation_text == null || fiscalisation_text.equals("null") || fiscalisation_text.isEmpty() ? "" : fiscalisation_text);
        jo.put("fiscalisation_qr", fiscalisation_qr == null || fiscalisation_qr.equals("null") || fiscalisation_qr.isEmpty() ? "" : fiscalisation_qr);
        jo.put("cashier", cashier_numericid);
        jo.put("training", training);
        jo.put("additional_text", additional_text);
        return jo;
    }
}
