package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Settings;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class SettingsSyncAdapter extends BaseSingleObjectSyncAdapter<Settings> {

    public SettingsSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, String key, PretixApi api, String syncCycleId, SyncManager.ProgressFeedback feedback) {
        super(store, eventSlug, key, api, syncCycleId, feedback);
    }

    @Override
    Settings getKnownObject() {
        List<Settings> is = store.select(Settings.class)
                .where(Settings.SLUG.eq(eventSlug))
                .get().toList();
        if (is.size() == 0) {
            return null;
        } else if (is.size() == 1) {
            return is.get(0);
        } else {
            // What's going on here? Let's delete and re-fetch
            store.delete(is);
            return null;
        }
    }

    @Override
    public void updateObject(Settings obj, JSONObject jsonobj) throws JSONException {
        obj.setSlug(eventSlug);
        obj.setName(jsonobj.optString("invoice_address_from_name"));
        obj.setAddress(jsonobj.optString("invoice_address_from"));
        obj.setZipcode(jsonobj.optString("invoice_address_from_zipcode"));
        obj.setCity(jsonobj.optString("invoice_address_from_city"));
        obj.setCountry(jsonobj.optString("invoice_address_from_country"));
        obj.setTax_id(jsonobj.optString("invoice_address_from_tax_id"));
        obj.setVat_id(jsonobj.optString("invoice_address_from_vat_id"));
        obj.setPretixpos_additional_receipt_text(jsonobj.optString("pretixpos_additional_receipt_text"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    protected String getUrl() {
        return api.eventResourceUrl(eventSlug, "settings");
    }

    @Override
    String getResourceName() {
        return "settings";
    }

    @Override
    Settings newEmptyObject() {
        return new Settings();
    }
}
