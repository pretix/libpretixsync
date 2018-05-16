package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.ItemCategory;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class ItemCategorySyncAdapter extends BaseDownloadSyncAdapter<ItemCategory, Long> {
    public ItemCategorySyncAdapter(BlockingEntityStore<Persistable> store, PretixApi api) {
        super(store, api);
    }

    @Override
    Iterator<ItemCategory> getKnownObjectsIterator() {
        return store.select(ItemCategory.class).get().iterator();
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
