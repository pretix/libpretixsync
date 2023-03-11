package eu.pretix.libpretixsync.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.requery.*;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import eu.pretix.libpretixsync.utils.I18nString;

@Entity(cacheable = false)
public class AbstractItem implements RemoteObject {
    @Generated
    @Key
    public Long id;

    public String event_slug;

    public Long server_id;

    public Long position;

    @Nullable
    public Long category_id;

    public boolean admission;

    public boolean active;

    @Column(definition = "TEXT")
    public String json_data;

    @ManyToMany
    @JsonIgnore
    List<Question> questions;

    @ManyToMany
    @JsonIgnore
    List<Quota> quotas;

    @Nullable
    public Long badge_layout_id;

    @Nullable
    public Long ticket_layout_id;

    @Nullable
    public Long ticket_layout_pretixpos_id;

    public String picture_filename;

    @Override
    public JSONObject getJSON() throws JSONException {
        return new JSONObject(json_data);
    }

    @JsonIgnore
    public boolean isPersonalized() {
        try {
            JSONObject j = getJSON();
            if (j.has("personalized")) {
                return j.getBoolean("personalized");
            } else {
                return admission;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    @JsonIgnore
    public String getInternalName() {
        try {
            String internal = getJSON().optString("internal_name");
            if (internal != null && !internal.isEmpty() && !"null".equals(internal)) {
                return internal;
            }
            return I18nString.toString(getJSON().getJSONObject("name"));
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    @JsonIgnore
    public String getName() {
        try {
            return I18nString.toString(getJSON().getJSONObject("name"));
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    @JsonIgnore
    public boolean isGiftcard() {
        try {
            return getJSON().getBoolean("issue_giftcard");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JsonIgnore
    public boolean isRequireBundling() {
        try {
            return getJSON().getBoolean("require_bundling");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JsonIgnore
    public boolean hasVariations() {
        try {
            return getJSON().getBoolean("has_variations");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JsonIgnore
    public Integer getMinPerOrder() {
        try {
            if (getJSON().isNull("min_per_order")) return null;
            return getJSON().optInt("min_per_order");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    @JsonIgnore
    public Integer getMaxPerOrder() {
        try {
            if (getJSON().isNull("max_per_order")) return null;
            return getJSON().optInt("max_per_order");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    @JsonIgnore
    public BigDecimal getDefaultPrice() {
        try {
            return new BigDecimal(getJSON().getString("default_price"));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BigDecimal(0.00);
        }
    }

    @JsonIgnore
    public boolean hasFreePrice() {
        try {
            if (getJSON().isNull("free_price")) {
                return false;
            }
            return getJSON().getBoolean("free_price");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JsonIgnore
    public boolean isGenerateTickets() {
        try {
            if (getJSON().isNull("generate_tickets")) {
                return true;
            }
            return getJSON().getBoolean("generate_tickets");
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    @JsonIgnore
    public Long getTaxRuleId() {
        try {
            return getJSON().optLong("tax_rule");
        } catch (JSONException e) {
            e.printStackTrace();
            return Long.valueOf(0);
        }
    }

    @JsonIgnore
    public boolean isRequireVoucher() {
        try {
            return getJSON().getBoolean("require_voucher");
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    @JsonIgnore
    public boolean isHideWithoutVoucher() {
        try {
            return getJSON().getBoolean("hide_without_voucher");
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    @JsonIgnore
    public boolean hasDynamicValidityWithCustomStart() {
        try {
            JSONObject jo = getJSON();
            if (!jo.optString("validity_mode", "").equals("dynamic")) {
                return false;
            }
            return jo.optBoolean("validity_dynamic_start_choice", false);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JsonIgnore
    public boolean hasDynamicValidityWithTimeOfDay() {
        try {
            JSONObject jo = getJSON();
            if ((!jo.isNull("validity_dynamic_duration_months") && jo.optLong("validity_dynamic_duration_months", 0) > 0) || (!jo.isNull("validity_dynamic_duration_days") && jo.optLong("validity_dynamic_duration_days", 0) > 0)) {
                return false;
            }
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    @JsonIgnore
    public Long dynamicValidityDayLimit() {
        try {
            JSONObject jo = getJSON();
            if (jo.has("validity_dynamic_start_choice_day_limit") && !jo.isNull("validity_dynamic_start_choice_day_limit")) {
                return jo.getLong("validity_dynamic_start_choice_day_limit");
            }
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    public boolean availableByTime() {
        try {
            JSONObject jo = getJSON();
            if (!jo.isNull("available_from")) {
                DateTime af = ISODateTimeFormat.dateTimeParser().parseDateTime(jo.getString("available_from"));
                if (af.isAfterNow()) {
                    return false;
                }
            }
            if (!jo.isNull("available_until")) {
                DateTime af = ISODateTimeFormat.dateTimeParser().parseDateTime(jo.getString("available_until"));
                if (af.isBeforeNow()) {
                    return false;
                }
            }
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    public boolean availableOnSalesChannel(String channel) {
        try {
            JSONArray channels = getJSON().optJSONArray("sales_channels");
            if (channels == null) {
                return true;
            }
            for (int i = 0; i < channels.length(); i++) {
                if (channels.getString(i).equals(channel)) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            e.printStackTrace();
            return true;
        }
    }

    public List<ItemVariation> getVariations() throws JSONException {
        List<ItemVariation> l = new ArrayList<>();
        JSONArray vars = getJSON().getJSONArray("variations");
        for (int i = 0; i < vars.length(); i++) {
            JSONObject variation = vars.getJSONObject(i);
            ItemVariation v = new ItemVariation();
            v.setActive(variation.getBoolean("active"));
            v.setDescription(variation.optJSONObject("description"));
            v.setPosition(variation.getLong("position"));
            v.setPrice(new BigDecimal(variation.getString("price")));
            v.setServer_id(variation.getLong("id"));
            v.setValue(variation.getJSONObject("value"));
            v.setAvailable_from(variation.optString("available_from"));
            v.setAvailable_until(variation.optString("available_until"));
            v.setSales_channels(variation.optJSONArray("sales_channels"));
            v.setHide_without_voucher(variation.optBoolean("hide_without_voucher", false));
            v.setCheckin_attention(variation.optBoolean("checkin_attention", false));
            l.add(v);
        }
        return l;
    }

    public ItemVariation getVariation(Long variation_id) throws JSONException {
        for (ItemVariation var : getVariations()) {
            if (var.getServer_id().equals(variation_id)) {
                return var;
            }
        }
        return null;
    }

    public List<ItemAddOn> getAddons() throws JSONException {
        List<ItemAddOn> l = new ArrayList<>();
        JSONArray objects = getJSON().getJSONArray("addons");
        for (int i = 0; i < objects.length(); i++) {
            JSONObject obj = objects.getJSONObject(i);
            ItemAddOn v = new ItemAddOn();
            v.setAddonCategoryId(obj.getLong("addon_category"));
            v.setMinCount(obj.getInt("min_count"));
            v.setMaxCount(obj.getInt("max_count"));
            v.setPosition(obj.getInt("position"));
            v.setMultiAllowed(obj.getBoolean("multi_allowed"));
            v.setPriceIncluded(obj.getBoolean("price_included"));
            l.add(v);
        }
        Collections.sort(l, Comparator.comparingInt(ItemAddOn::getPosition));
        return l;
    }

    public List<ItemBundle> getBundles() throws JSONException {
        List<ItemBundle> l = new ArrayList<>();
        JSONArray objects = getJSON().getJSONArray("bundles");
        for (int i = 0; i < objects.length(); i++) {
            JSONObject obj = objects.getJSONObject(i);
            ItemBundle v = new ItemBundle();
            v.setBundledItemId(obj.getLong("bundled_item"));
            v.setBundledVariationId(obj.isNull("bundled_variation") ? null : obj.getLong("bundled_variation"));
            v.setCount(obj.getInt("count"));
            v.setDesignatedPrice(obj.isNull("designated_price") ? null : new BigDecimal(obj.getString("designated_price")));
            l.add(v);
        }
        return l;
    }

    public enum MediaPolicy {
        NONE,
        REUSE,
        NEW,
        REUSE_OR_NEW,
    }

    @JsonIgnore
    public MediaPolicy getMediaPolicy() {
        try {
            String mp = getJSON().optString("media_policy");
            if (mp == null) return MediaPolicy.NONE;
            if (mp.equals("reuse")) return MediaPolicy.REUSE;
            if (mp.equals("new")) return MediaPolicy.NEW;
            if (mp.equals("reuse_or_new")) return MediaPolicy.REUSE_OR_NEW;
            return MediaPolicy.NONE;
        } catch (JSONException e) {
            e.printStackTrace();
            return MediaPolicy.NONE;
        }
    }

    @JsonIgnore
    public ReusableMediaType getMediaType() {
        try {
            String mp = getJSON().optString("media_type");
            if (mp == null) return ReusableMediaType.NONE;
            if (mp.equals("barcode")) return ReusableMediaType.BARCODE;
            if (mp.equals("nfc_uid")) return ReusableMediaType.NFC_UID;
            return ReusableMediaType.UNSUPPORTED;
        } catch (JSONException e) {
            e.printStackTrace();
            return ReusableMediaType.NONE;
        }
    }
}
