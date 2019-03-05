package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.TaxRule;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;

public class TaxRuleSyncAdapter extends BaseConditionalSyncAdapter<TaxRule, Long> {
    public TaxRuleSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    @Override
    public void updateObject(TaxRule obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    public Iterator<TaxRule> runBatch(List<Long> ids) {
        return store.select(TaxRule.class)
                .where(TaxRule.EVENT_SLUG.eq(eventSlug))
                .and(TaxRule.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    Iterator<Tuple> getKnownIDsIterator() {
        return store.select(TaxRule.SERVER_ID)
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
