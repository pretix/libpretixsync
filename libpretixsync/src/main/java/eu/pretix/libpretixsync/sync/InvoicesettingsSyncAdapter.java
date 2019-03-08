package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Invoicesettings;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class InvoicesettingsSyncAdapter extends BaseSingleObjectSyncAdapter<Invoicesettings> {
    public InvoicesettingsSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, String key, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, eventSlug, key, api, feedback);
    }

    @Override
    Invoicesettings getKnownObject() {
        return store.select(Invoicesettings.class)
                .where(Invoicesettings.SLUG.eq(key))
                .get().firstOrNull();
    }

    @Override
    public void updateObject(Invoicesettings obj, JSONObject jsonobj) throws JSONException {
        obj.setSlug(eventSlug);
        obj.setName(jsonobj.getString("invoice_address_from_name"));
        obj.setAddress(jsonobj.getString("invoice_address_from"));
        obj.setZipcode(jsonobj.getString("invoice_address_from_zipcode"));
        obj.setCity(jsonobj.getString("invoice_address_from_city"));
        obj.setCountry(jsonobj.getString("invoice_address_from_country"));
        obj.setTax_id(jsonobj.getString("invoice_address_from_tax_id"));
        obj.setVat_id(jsonobj.getString("invoice_address_from_vat_id"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    protected String getUrl() {
        return api.eventResourceUrl("invoicesettings");
    }

    @Override
    String getResourceName() {
        return "invoicesettings";
    }

    @Override
    Invoicesettings newEmptyObject() {
        return new Invoicesettings();
    }
}
