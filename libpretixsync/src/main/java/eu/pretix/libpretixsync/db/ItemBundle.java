package eu.pretix.libpretixsync.db;

import java.io.Serializable;
import java.math.BigDecimal;

public class ItemBundle implements Serializable {
    private Long bundledItemId;
    private Long bundledVariationId;
    private int count;
    private BigDecimal designatedPrice;

    public Long getBundledItemId() {
        return bundledItemId;
    }

    public void setBundledItemId(Long bundledItemId) {
        this.bundledItemId = bundledItemId;
    }

    public Long getBundledVariationId() {
        return bundledVariationId;
    }

    public void setBundledVariationId(Long bundledVariationId) {
        this.bundledVariationId = bundledVariationId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public BigDecimal getDesignatedPrice() {
        return designatedPrice;
    }

    public void setDesignatedPrice(BigDecimal designatedPrice) {
        this.designatedPrice = designatedPrice;
    }
}
