package eu.pretix.libpretixsync.db;

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
import io.requery.ManyToOne;
import io.requery.Nullable;
import io.requery.OneToMany;
import io.requery.OneToOne;

@Entity(cacheable = false)
public class AbstractReceiptLine implements LocalObject {
    @Generated
    @Key
    public Long id;

    @ForeignKey
    @ManyToOne
    public Receipt receipt;

    public String type;

    public Long positionid;

    public boolean canceled;

    public BigDecimal price;

    public BigDecimal tax_rate;

    public Long tax_rule;

    public BigDecimal tax_value;

    @Nullable
    public String secret;

    @Nullable
    public Long subevent_id;

    @Nullable
    public Long item_id;

    @Nullable
    public Long variation_id;

    public String sale_text;

    @ForeignKey
    @ManyToOne
    @Nullable
    public ReceiptLine addon_to;

    @OneToMany
    public List<ReceiptLine> addons;

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("id", id);
        jo.put("type", type);
        jo.put("positionid", positionid);
        jo.put("canceled", canceled);
        jo.put("price", price);
        jo.put("tax_rate", tax_rate);
        jo.put("tax_value", tax_value);
        jo.put("secret", secret);
        jo.put("subevent", subevent_id);
        jo.put("item", item_id);
        jo.put("variation", variation_id);
        jo.put("sale_text", sale_text);
        return jo;
    }
}
