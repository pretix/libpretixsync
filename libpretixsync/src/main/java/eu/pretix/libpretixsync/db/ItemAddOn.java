package eu.pretix.libpretixsync.db;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigDecimal;

import eu.pretix.libpretixsync.utils.I18nString;

public class ItemAddOn implements Serializable {
    private Long addonCategoryId;
    private int minCount;
    private int maxCount;
    private int position;
    private boolean multiAllowed;
    private boolean priceIncluded;

    public Long getAddonCategoryId() {
        return addonCategoryId;
    }

    public void setAddonCategoryId(Long addonCategoryId) {
        this.addonCategoryId = addonCategoryId;
    }

    public int getMinCount() {
        return minCount;
    }

    public void setMinCount(int minCount) {
        this.minCount = minCount;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isMultiAllowed() {
        return multiAllowed;
    }

    public void setMultiAllowed(boolean multiAllowed) {
        this.multiAllowed = multiAllowed;
    }

    public boolean isPriceIncluded() {
        return priceIncluded;
    }

    public void setPriceIncluded(boolean priceIncluded) {
        this.priceIncluded = priceIncluded;
    }
}
