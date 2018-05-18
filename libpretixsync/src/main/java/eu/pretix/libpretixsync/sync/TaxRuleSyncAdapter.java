package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.ItemCategory;
import eu.pretix.libpretixsync.db.TaxRule;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class TaxRuleSyncAdapter extends BaseConditionalSyncAdapter<TaxRule, Long> {
    public TaxRuleSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, PretixApi api) {
        super(store, eventSlug, api);
    }

    @Override
    protected void updateObject(TaxRule obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    Iterator<TaxRule> getKnownObjectsIterator() {
        return store.select(TaxRule.class)
                .where(TaxRule.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "taxrules";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(TaxRule obj) {
        return obj.getServer_id();
    }

    @Override
    TaxRule newEmptyObject() {
        return new TaxRule();
    }
}
