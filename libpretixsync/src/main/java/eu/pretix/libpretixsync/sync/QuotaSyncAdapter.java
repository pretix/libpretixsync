package eu.pretix.libpretixsync.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.Quota;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;

public class QuotaSyncAdapter extends BaseConditionalSyncAdapter<Quota, Long> {
    public QuotaSyncAdapter(BlockingEntityStore<Persistable> store, String eventSlug, PretixApi api) {
        super(store, eventSlug, api);
    }

    @Override
    public void updateObject(Quota obj, JSONObject jsonobj) throws JSONException {
        obj.setEvent_slug(eventSlug);
        obj.setServer_id(jsonobj.getLong("id"));
        obj.setSubevent_id(jsonobj.optLong("subevent"));
        obj.setJson_data(jsonobj.toString());
        JSONArray itemsarr = jsonobj.getJSONArray("items");
        List<Long> itemids = new ArrayList<>();
        for (int i = 0; i < itemsarr.length(); i++) {
            itemids.add(itemsarr.getLong(i));
        }
        List<Item> items = store.select(Item.class).where(
                Item.SERVER_ID.in(itemids)
        ).get().toList();
        for (Item item : items) {
            if (!obj.getItems().contains(item)) {
                obj.getItems().add(item);
            }
        }
        obj.getItems().retainAll(items);
    }

    @Override
    Iterator<Quota> getKnownObjectsIterator() {
        return store.select(Quota.class)
                .where(Quota.EVENT_SLUG.eq(eventSlug))
                .get().iterator();
    }

    @Override
    String getResourceName() {
        return "quotas";
    }

    @Override
    Long getId(JSONObject obj) throws JSONException {
        return obj.getLong("id");
    }

    @Override
    Long getId(Quota obj) {
        return obj.getServer_id();
    }

    @Override
    Quota newEmptyObject() {
        return new Quota();
    }
}
