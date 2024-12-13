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

    public String getCode() {
        try {
            if (!getJSON().has("code") || getJSON().isNull("code")) {
                return null;
            }
            return getJSON().getString("code");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
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

    public BigDecimal calculateTaxFromNet(BigDecimal price) {
        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
        BigDecimal gross = price
                .multiply(getRate().divide(new BigDecimal("100.00", mc), mc).add(new BigDecimal("1.00", mc), mc), mc)
                .setScale(2, RoundingMode.HALF_UP);
        return gross.subtract(price);
    }

    public BigDecimal calculateTaxFromGross(BigDecimal price) {
        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
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
    }

    public BigDecimal calculateTax(BigDecimal price) {
        if (includesTax()) {
            return calculateTaxFromGross(price);
        } else {
            return calculateTaxFromNet(price);
        }
    }

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }
}
