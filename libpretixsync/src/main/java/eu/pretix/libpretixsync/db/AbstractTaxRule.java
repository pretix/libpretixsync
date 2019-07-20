package eu.pretix.libpretixsync.db;

import io.requery.Column;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;

@Entity(cacheable = false)
public class AbstractTaxRule implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public Long server_id;

    public String event_slug;

    @Column(definition = "TEXT")
    public String json_data;

    public boolean includesTax() {
        try {
            return getJSON().getBoolean("price_includes_tax");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public BigDecimal getRate() {
        try {
            return new BigDecimal(getJSON().getString("rate"));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BigDecimal(0.00);
        }
    }

    public BigDecimal calculatePrice(BigDecimal price) {
        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
        if (includesTax()) {
            return price;
        } else {
            BigDecimal gross = price
                    .multiply(getRate().divide(new BigDecimal("100.00", mc), mc).add(new BigDecimal("1.00", mc), mc), mc)
                    .setScale(2, RoundingMode.HALF_UP);
            return gross;
        }
    }

    public BigDecimal calculateTax(BigDecimal price) {
        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
        if (includesTax()) {
            BigDecimal net = price
                    .subtract(
                            price.multiply(
                                    new BigDecimal("1.00", mc).subtract(
                                            (new BigDecimal("100.00", mc)).divide(
                                                    new BigDecimal("100.00", mc).add(getRate(), mc),
                                                    mc
                                            ),
                                            mc
                                    ),
                                    mc
                            ),
                            mc
                    )
                    .setScale(2, RoundingMode.HALF_UP);
            return price.subtract(net);
        } else {
            BigDecimal gross = price
                    .multiply(getRate().divide(new BigDecimal("100.00", mc), mc).add(new BigDecimal("1.00", mc), mc), mc)
                    .setScale(2, RoundingMode.HALF_UP);
            return gross.subtract(price);
        }
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
