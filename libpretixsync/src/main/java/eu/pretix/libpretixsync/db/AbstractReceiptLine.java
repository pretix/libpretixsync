package eu.pretix.libpretixsync.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

import eu.pretix.libpretixsync.BuildConfig;
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
public class AbstractReceiptLine implements LocalObject {
    public static String TYPE_PRODUCT_SALE = "PRODUCT_SALE";
    public static String TYPE_PRODUCT_RETURN = "PRODUCT_RETURN";
    public static String TYPE_CHANGE_IN = "CHANGE_IN";
    public static String TYPE_CHANGE_START = "CHANGE_START";
    public static String TYPE_CHANGE_OUT = "CHANGE_OUT";
    public static String TYPE_CHANGE_DIFF = "CHANGE_DIFF";
    public static String TYPE_GIFTCARD_SALE = "GIFTCARD_SALE";
    public static String TYPE_GIFTCARD_REDEMPTION = "GIFTCARD_REDEMPTION";
    public static String TYPE_PAY_ORDER = "PAY_ORDER";
    public static String TYPE_PAY_ORDER_REVERSE = "PAY_ORDER_REVERSE";

    @Generated
    @Key
    public Long id;

    @ForeignKey
    @ManyToOne
    @Index
    public Receipt receipt;

    public String type;

    public Long positionid;

    public boolean canceled;

    public BigDecimal price;

    public BigDecimal tax_rate;

    @Column(value = BuildConfig.BOOLEAN_FALSE)
    public boolean price_calculated_from_net;

    @Column(value = BuildConfig.BOOLEAN_FALSE)
    public boolean canceled_because_of_receipt;

    public Long tax_rule;

    public BigDecimal tax_value;

    @Nullable
    public BigDecimal listed_price;

    @Nullable
    public BigDecimal price_after_voucher;

    @Nullable
    public BigDecimal custom_price_input;

    @Nullable
    public String secret;

    @Nullable
    public String voucher_code;

    @Nullable
    public Long subevent_id;

    @Nullable
    public String subevent_text;

    @Nullable
    public String event_date_from;

    @Nullable
    public String event_date_to;

    @Nullable
    public String seat_guid;

    @Nullable
    public String seat_name;

    @Nullable
    public Long item_id;

    @Nullable
    public Long variation_id;

    public String sale_text;

    @Nullable
    public Long cart_position_id;

    @Nullable
    public Date created;

    @Nullable
    public String cart_id;

    @Nullable
    public String remote_error;

    @Nullable
    public Date cart_expires;

    @ForeignKey
    @ManyToOne
    @Nullable
    @Index
    public ReceiptLine addon_to;

    @Column(value = BuildConfig.BOOLEAN_FALSE)
    public boolean is_bundled;

    @OneToMany
    public List<ReceiptLine> addons;

    @Nullable
    public String answers;

    @Nullable
    public String attendee_name;

    @Nullable
    public String attendee_email;

    @Nullable
    public String attendee_company;

    @Nullable
    public String attendee_street;

    @Nullable
    public String attendee_zipcode;

    @Nullable
    public String attendee_city;

    @Nullable
    public String attendee_country;

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("id", id);
        jo.put("type", type);
        jo.put("position_id", positionid);
        jo.put("canceled", canceled);
        jo.put("canceled_because_of_receipt", canceled_because_of_receipt);
        jo.put("price_calculated_from_net", price_calculated_from_net);
        jo.put("listed_price", listed_price != null ? listed_price.setScale(2, RoundingMode.HALF_UP) : null);
        jo.put("price_after_voucher", price_after_voucher != null ? price_after_voucher.setScale(2, RoundingMode.HALF_UP) : null);
        jo.put("custom_price_input", custom_price_input != null ? custom_price_input.setScale(2, RoundingMode.HALF_UP) : null);
        jo.put("voucher_code", voucher_code);
        jo.put("price", price.setScale(2, RoundingMode.HALF_UP));
        jo.put("tax_rate", tax_rate != null ? tax_rate.setScale(2, RoundingMode.HALF_UP) : "0.00");
        jo.put("tax_value", tax_value != null ? tax_value.setScale(2, RoundingMode.HALF_UP) : "0.00");
        jo.put("tax_rule", tax_rule != null ? tax_rule : JSONObject.NULL);
        jo.put("secret", secret);
        jo.put("seat", seat_guid != null ? seat_guid : JSONObject.NULL);
        jo.put("subevent", subevent_id);
        jo.put("event_date_from", event_date_from != null && event_date_from.length() > 5 ? event_date_from : JSONObject.NULL);
        jo.put("event_date_to", event_date_to != null && event_date_to.length() > 5 ? event_date_to : JSONObject.NULL);
        jo.put("subevent_text", subevent_text != null && subevent_text.length() > 0 && !subevent_text.equals("null") ? subevent_text : JSONObject.NULL);
        jo.put("item", item_id != null && item_id != 0L ? item_id : JSONObject.NULL);
        jo.put("variation", variation_id);
        jo.put("answers", answers);
        jo.put("sale_text", sale_text);
        try {
            AbstractReceiptLine addon_to = (AbstractReceiptLine) this.getClass().getMethod("getAddon_to", null).invoke(this);  // Accessing addon_to directly does not always work because … requery sucks
            jo.put("addon_to", addon_to != null ? addon_to.positionid : JSONObject.NULL);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        jo.put("is_bundled", is_bundled);
        jo.put("attendee_name", attendee_name);
        jo.put("attendee_email", attendee_email);
        jo.put("attendee_company", attendee_company);
        jo.put("attendee_street", attendee_street);
        jo.put("attendee_zipcode", attendee_zipcode);
        jo.put("attendee_city", attendee_city);
        jo.put("attendee_country", attendee_country);
        return jo;
    }
}
