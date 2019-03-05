package eu.pretix.libpretixsync.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Item;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import io.requery.query.Tuple;

public class ItemSyncAdapter extends BaseConditionalSyncAdapter<Item, Long> {
    public ItemSyncAdapter(BlockingEntityStore<Persistable> store, FileStorage fileStorage, String eventSlug, PretixApi api) {
        super(store, fileStorage, eventSlug, api);
    }

    @Override
    public void updateObject(Item obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setPosition(jsonobj.getLong("position"));
        obj.setCategory_id(jsonobj.optLong("category"));
        obj.setAdmission(jsonobj.optBoolean("admission", false));
        obj.setActive(jsonobj.optBoolean("active", true));
        obj.setJson_data(jsonobj.toString());
    }

    @Override
    public Iterator<Item> runBatch(List<Long> ids) {
        return store.select(Item.class)
                .where(Item.EVENT_SLUG.eq(eventSlug))
                .and(Item.SERVER_ID.in(ids))
                .get().iterator();
    }

    @Override
    Iterator<Tuple> getKnownIDsIterator() {
        return store.select(Item.SERVER_ID)
                .where(Item.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "items";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(Item obj) {
        return obj.getServer_id();
    }

    @Override
    Item newEmptyObject() {
        return new Item();
    }
}
