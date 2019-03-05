package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.ItemCategory;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;

public class ItemCategorySyncAdapter extends BaseConditionalSyncAdapter<ItemCategory, Long> {
    public ItemCategorySyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api, SyncManager.ProgressFeedback feedback) {
        super(store, fileStorage, eventSlug, api, feedback);
    }

    @Override
    public void updateObject(ItemCategory obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setPosition(jsonobj.getLong("position"));
        obj.setIs_addon(jsonobj.optBoolean("is_addon", false));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    public Iterator<ItemCategory> runBatch(List<Long> ids) {
        return store.select(ItemCategory.class)
                .where(ItemCategory.EVENT_SLUG.eq(eventSlug))
                .and(ItemCategory.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    Iterator<Tuple> getKnownIDsIterator() {
        return store.select(ItemCategory.SERVER_ID)
                .where(ItemCategory.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "categories";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(ItemCategory obj) {
        return obj.getServer_id();
    }

    @Override
    ItemCategory newEmptyObject() {
        return new ItemCategory();
    }
}
